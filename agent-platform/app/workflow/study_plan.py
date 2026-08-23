from __future__ import annotations

import hashlib
import json
import os
import re
from uuid import uuid4

from app.agents.goal_agent import GoalAgent
from app.agents.plan_agent import PlanAgent
from app.agents.plan_validator_agent import PlanValidatorAgent
from app.agents.replan_agent import ReplanAgent
from app.agents.scheduler_agent import SchedulerAgent
from app.models.goal import GoalRequest
from app.models.plan import PlanResponse, PlanResponseV2
from app.models.workflow import (
    StudyPlanState,
    WorkflowNode,
    WorkflowStatus,
    workflow_request_fingerprint,
)
from app.observability import agent_span, current_trace_id, record_workflow_transition
from app.workflow.checkpoint import CheckpointStore, PostgresCheckpointStore, WorkflowStateError


class WorkflowValidationExhausted(RuntimeError):
    """Raised when a plan remains invalid after the bounded repair budget."""


class WorkflowRepairStalled(RuntimeError):
    """Raised when automatic repair repeats a previously checkpointed plan."""


class StatefulStudyOrchestrator:
    """Node-based study-plan workflow with durable, idempotent resume."""

    def __init__(self, checkpoint_store: CheckpointStore | None = None) -> None:
        self.goal_agent = GoalAgent()
        self.scheduler_agent = SchedulerAgent()
        self.plan_agent = PlanAgent()
        self.plan_validator_agent = PlanValidatorAgent()
        self.replan_agent = ReplanAgent()
        self.checkpoint_store = checkpoint_store or PostgresCheckpointStore()

    async def run_study_plan(self, goal: GoalRequest, trace_id: str | None = None) -> PlanResponse:
        result = await self.run_study_plan_v2(goal, trace_id=trace_id)
        return PlanResponse(
            plan_id=result.plan_id,
            title=result.title,
            start_date=result.start_date,
            end_date=result.end_date,
            days=result.days,
        )

    async def run_study_plan_v2(self, goal: GoalRequest, trace_id: str | None = None) -> PlanResponseV2:
        real_trace_id = trace_id or current_trace_id() or str(uuid4())
        checkpoints_enabled = os.getenv(
            "LEARNFLOW_WORKFLOW_CHECKPOINTS_ENABLED", "true"
        ).strip().lower() in {"1", "true", "yes", "on"}
        if goal.workflow_id is None or not checkpoints_enabled:
            return await self._run_ephemeral(goal, real_trace_id)

        fingerprint = workflow_request_fingerprint(goal)
        state = self.checkpoint_store.load(goal.workflow_id, fingerprint)
        if state is None:
            state = StudyPlanState(
                workflow_id=goal.workflow_id,
                request_fingerprint=fingerprint,
                trace_id=real_trace_id,
                goal=goal,
            )
        elif state.status == WorkflowStatus.CANCELLED:
            raise WorkflowStateError("workflow is cancelled")
        elif state.status == WorkflowStatus.SUCCEEDED:
            return self._response(state)
        elif state.status == WorkflowStatus.READY_TO_SAVE:
            return self._response(state)
        else:
            state.status = WorkflowStatus.RUNNING
            state.last_error_code = None
        self._ensure_plan_tracking(state)

        try:
            if self._begin(state, WorkflowNode.GOAL):
                with agent_span("GoalAgent", "goal-blueprint-v1"):
                    state.goal_blueprint = await self.goal_agent.run(goal, trace_id=real_trace_id)
                self._complete(state, WorkflowNode.GOAL)

            if self._begin(state, WorkflowNode.SCHEDULE):
                if state.goal_blueprint is None:
                    raise WorkflowStateError("GOAL checkpoint is incomplete")
                with agent_span("SchedulerAgent", "scheduler-rules-v1"):
                    schedule = self.scheduler_agent.build_schedule(
                        goal,
                        state.goal_blueprint,
                        trace_id=real_trace_id,
                    )
                state.phases = schedule.phases
                state.weeks = schedule.weeks
                self._complete(state, WorkflowNode.SCHEDULE)

            if self._begin(state, WorkflowNode.PLAN):
                if state.goal_blueprint is None:
                    raise WorkflowStateError("GOAL checkpoint is incomplete")
                with agent_span("PlanAgent", "study-plan-v1"):
                    state.plan = await self.plan_agent.run(
                        goal,
                        goal_structure=state.goal_blueprint,
                        phases=state.phases,
                        weeks=state.weeks,
                        trace_id=real_trace_id,
                    )
                self._register_plan(state, state.plan)
                self._complete(state, WorkflowNode.PLAN)

            if self._begin(state, WorkflowNode.VALIDATE):
                self._validate_state(state, goal, real_trace_id)
                self._complete(state, WorkflowNode.VALIDATE)

            await self._repair_until_valid(state, goal, real_trace_id)
            if state.node_attempt(WorkflowNode.REPLAN) == 0 and WorkflowNode.REPLAN not in state.completed_nodes:
                state.current_node = WorkflowNode.REPLAN
                self._complete(state, WorkflowNode.REPLAN, outcome="SKIPPED")

            if WorkflowNode.SAVE not in state.completed_nodes:
                state.begin_node(WorkflowNode.SAVE)
                state.status = WorkflowStatus.READY_TO_SAVE
                self.checkpoint_store.save(state, WorkflowNode.SAVE, "PAUSED")
                record_workflow_transition(WorkflowNode.SAVE.value, "ready_to_save")
            return self._response(state)
        except Exception as failure:
            state.status = WorkflowStatus.FAILED
            state.last_error_code = self._error_code(failure)
            self.checkpoint_store.save(state, state.current_node, "FAILED")
            record_workflow_transition(state.current_node.value, "failed")
            raise

    async def _run_ephemeral(self, goal: GoalRequest, trace_id: str) -> PlanResponseV2:
        with agent_span("GoalAgent", "goal-blueprint-v1"):
            goal_blueprint = await self.goal_agent.run(goal, trace_id=trace_id)
        with agent_span("SchedulerAgent", "scheduler-rules-v1"):
            schedule = self.scheduler_agent.build_schedule(goal, goal_blueprint, trace_id=trace_id)
        with agent_span("PlanAgent", "study-plan-v1"):
            plan = await self.plan_agent.run(
                goal,
                goal_structure=goal_blueprint,
                phases=schedule.phases,
                weeks=schedule.weeks,
                trace_id=trace_id,
            )
        with agent_span("PlanValidatorAgent", "plan-validator-rules-v1", operation="validate"):
            validation_report = self.plan_validator_agent.validate(
                plan,
                goal=goal,
                goal_structure=goal_blueprint,
                trace_id=trace_id,
            )
        return PlanResponseV2(
            plan_id=plan.plan_id,
            title=plan.title,
            start_date=plan.start_date,
            end_date=plan.end_date,
            days=plan.days,
            trace_id=trace_id,
            goal_blueprint=goal_blueprint,
            phases=schedule.phases,
            weeks=schedule.weeks,
            validation_report=validation_report,
        )

    def _begin(self, state: StudyPlanState, node: WorkflowNode) -> bool:
        if node in state.completed_nodes:
            record_workflow_transition(node.value, "resumed")
            return False
        self._start(state, node)
        return True

    def _start(self, state: StudyPlanState, node: WorkflowNode) -> None:
        state.begin_node(node)
        self.checkpoint_store.save(state, node, "STARTED")
        record_workflow_transition(node.value, "started")

    def _complete(self, state: StudyPlanState, node: WorkflowNode, outcome: str = "COMPLETED") -> None:
        state.complete_node(node)
        self.checkpoint_store.save(state, node, outcome)
        record_workflow_transition(node.value, outcome.lower())

    async def _repair_until_valid(
        self,
        state: StudyPlanState,
        goal: GoalRequest,
        trace_id: str,
    ) -> None:
        max_repairs = self._max_replan_attempts()
        while True:
            if state.plan is None:
                raise WorkflowStateError("PLAN checkpoint is incomplete")
            if state.validation_report is None or state.validated_revision < state.plan_revision:
                self._start(state, WorkflowNode.VALIDATE)
                self._validate_state(state, goal, trace_id)
                self._complete(state, WorkflowNode.VALIDATE)
                continue
            if state.validation_report.is_valid:
                return
            if state.node_attempt(WorkflowNode.REPLAN) >= max_repairs:
                raise WorkflowValidationExhausted(
                    f"plan validation failed after {max_repairs} automatic repair attempts"
                )

            self._start(state, WorkflowNode.REPLAN)
            with agent_span("ReplanAgent", "validator-repair-rules-v1", operation="replan"):
                candidate = await self.replan_agent.repair_validation(
                    state.plan,
                    goal,
                    state.validation_report,
                    goal_structure=state.goal_blueprint,
                    trace_id=trace_id,
                )
            self._assert_repair_invariants(state.plan, candidate, goal)
            fingerprint = self._plan_fingerprint(candidate)
            if fingerprint in state.plan_fingerprints:
                raise WorkflowRepairStalled("automatic repair repeated a checkpointed plan")
            state.plan = candidate
            state.plan_revision += 1
            state.plan_fingerprints.append(fingerprint)
            state.validation_report = None
            self._complete(state, WorkflowNode.REPLAN)

    def _validate_state(self, state: StudyPlanState, goal: GoalRequest, trace_id: str) -> None:
        if state.plan is None:
            raise WorkflowStateError("PLAN checkpoint is incomplete")
        with agent_span("PlanValidatorAgent", "plan-validator-rules-v1", operation="validate"):
            state.validation_report = self.plan_validator_agent.validate(
                state.plan,
                goal=goal,
                goal_structure=state.goal_blueprint,
                trace_id=trace_id,
            )
        state.validated_revision = state.plan_revision

    def _ensure_plan_tracking(self, state: StudyPlanState) -> None:
        if state.plan is not None and not state.plan_fingerprints:
            state.plan_fingerprints.append(self._plan_fingerprint(state.plan))
        if (
            state.plan is not None
            and state.validation_report is not None
            and WorkflowNode.VALIDATE in state.completed_nodes
            and state.validated_revision < 0
        ):
            state.validated_revision = state.plan_revision

    def _register_plan(self, state: StudyPlanState, plan: PlanResponse) -> None:
        fingerprint = self._plan_fingerprint(plan)
        if not state.plan_fingerprints:
            state.plan_fingerprints.append(fingerprint)

    @staticmethod
    def _plan_fingerprint(plan: PlanResponse) -> str:
        payload = json.dumps(
            plan.model_dump(mode="json"),
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    @staticmethod
    def _assert_repair_invariants(
        previous: PlanResponse,
        candidate: PlanResponse,
        goal: GoalRequest,
    ) -> None:
        if len(candidate.days) != len(previous.days):
            raise WorkflowStateError("automatic repair changed the plan day count")
        previous_shape = [(day.day_index, day.date) for day in previous.days]
        candidate_shape = [(day.day_index, day.date) for day in candidate.days]
        if candidate_shape != previous_shape:
            raise WorkflowStateError("automatic repair changed day indexes or dates")
        max_minutes = max(30, goal.hours_per_day * 90)
        if any((day.estimated_minutes or 0) > max_minutes for day in candidate.days):
            raise WorkflowStateError("automatic repair exceeded the daily load budget")
        if any(not day.tasks or len(day.tasks) > 4 for day in candidate.days):
            raise WorkflowStateError("automatic repair produced an invalid task count")

    @staticmethod
    def _max_replan_attempts() -> int:
        enabled = os.getenv("LEARNFLOW_WORKFLOW_AUTO_REPLAN_ENABLED", "true").strip().lower()
        if enabled not in {"1", "true", "yes", "on"}:
            return 0
        try:
            configured = int(os.getenv("LEARNFLOW_WORKFLOW_MAX_REPLAN_ATTEMPTS", "2"))
        except ValueError:
            configured = 2
        return max(0, min(2, configured))

    @staticmethod
    def _response(state: StudyPlanState) -> PlanResponseV2:
        if state.plan is None:
            raise WorkflowStateError("workflow does not contain a generated plan")
        return PlanResponseV2(
            plan_id=state.plan.plan_id,
            title=state.plan.title,
            start_date=state.plan.start_date,
            end_date=state.plan.end_date,
            days=state.plan.days,
            trace_id=state.trace_id,
            goal_blueprint=state.goal_blueprint,
            phases=state.phases,
            weeks=state.weeks,
            validation_report=state.validation_report,
            workflow_id=state.workflow_id,
            workflow_status=state.status.value,
            completed_node=state.current_node.value,
            state_schema_version=state.schema_version,
        )

    @staticmethod
    def _error_code(failure: Exception) -> str:
        value = re.sub(r"[^A-Za-z0-9_]", "_", failure.__class__.__name__).upper()
        return (value or "WORKFLOW_ERROR")[:64]
