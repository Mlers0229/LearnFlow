from __future__ import annotations

import asyncio
from datetime import date, timedelta
from unittest.mock import AsyncMock, MagicMock
from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.agents.plan_validator_agent import PlanValidatorAgent
from app.agents.replan_agent import ReplanAgent
from app.models.goal import GoalPlanStructure, GoalRequest, GoalTopic
from app.models.plan import (
    LearningPhase,
    PlanDay,
    PlanResponse,
    PlanValidationReport,
    SchedulePlan,
    WeeklyPlan,
)
from app.models.workflow import (
    StudyPlanState,
    WorkflowNode,
    WorkflowStatus,
    state_checksum,
    workflow_request_fingerprint,
)
from app.workflow.checkpoint import WorkflowStateError
from app.workflow.study_plan import (
    StatefulStudyOrchestrator,
    WorkflowRepairStalled,
    WorkflowValidationExhausted,
)


class MemoryCheckpointStore:
    def __init__(self) -> None:
        self.state: StudyPlanState | None = None
        self.events: list[tuple[WorkflowNode, str, int]] = []

    def load(self, workflow_id, request_fingerprint):
        if self.state is None:
            return None
        if self.state.workflow_id != workflow_id:
            return None
        if self.state.request_fingerprint != request_fingerprint:
            raise WorkflowStateError("workflow request fingerprint mismatch")
        return self.state.model_copy(deep=True)

    def save(self, state, node, outcome):
        self.state = state.model_copy(deep=True)
        self.events.append((node, outcome, state.node_attempt(node)))
        return len(self.events)


def goal(workflow_id=None, goal_text="Learn Java") -> GoalRequest:
    return GoalRequest(
        goal_text=goal_text,
        duration_weeks=1,
        hours_per_day=1,
        level="beginner",
        workflow_id=workflow_id,
    )


def generated_plan() -> PlanResponse:
    return PlanResponse(
        plan_id="draft-plan",
        title="Java plan",
        start_date=date(2026, 8, 22),
        end_date=date(2026, 8, 22),
        days=[
            PlanDay(
                date=date(2026, 8, 22),
                title="Java basics",
                tasks=["Read", "Practice"],
                day_index=1,
            )
        ],
    )


def invalid_report() -> PlanValidationReport:
    return PlanValidationReport(
        is_valid=False,
        issues=[
            {
                "code": "unbalanced_load",
                "message": "load drift",
                "severity": "error",
            }
        ],
        load_balance_score=20,
        suggested_fixes=["rebalance"],
    )


def revised_plan(title: str) -> PlanResponse:
    plan = generated_plan()
    plan.title = title
    return plan


def configure_agents(orchestrator: StatefulStudyOrchestrator) -> None:
    blueprint = GoalPlanStructure(
        topics=[GoalTopic(id="java", name="Java", description="Core Java", order=1)]
    )
    schedule = SchedulePlan(
        phases=[LearningPhase(phase_id="p1", title="Core", goal="Learn", weeks=1)],
        weeks=[
            WeeklyPlan(
                week_index=1,
                phase_id="p1",
                theme="Basics",
                target_hours=7,
            )
        ],
    )
    orchestrator.goal_agent.run = AsyncMock(return_value=blueprint)
    orchestrator.scheduler_agent.build_schedule = MagicMock(return_value=schedule)
    orchestrator.plan_agent.run = AsyncMock(return_value=generated_plan())
    orchestrator.plan_validator_agent.validate = MagicMock(
        return_value=PlanValidationReport(is_valid=True)
    )
    orchestrator.replan_agent.repair_validation = AsyncMock(return_value=revised_plan("repaired"))


def test_failed_node_resumes_without_repeating_completed_nodes() -> None:
    store = MemoryCheckpointStore()
    orchestrator = StatefulStudyOrchestrator(store)
    configure_agents(orchestrator)
    orchestrator.plan_agent.run = AsyncMock(
        side_effect=[RuntimeError("temporary model failure"), generated_plan()]
    )
    request = goal(uuid4())

    with pytest.raises(RuntimeError, match="temporary model failure"):
        asyncio.run(orchestrator.run_study_plan_v2(request, trace_id="trace-one"))

    assert store.state is not None
    assert store.state.status == WorkflowStatus.FAILED
    assert store.state.completed_nodes == [WorkflowNode.GOAL, WorkflowNode.SCHEDULE]

    response = asyncio.run(orchestrator.run_study_plan_v2(request, trace_id="trace-two"))

    assert response.workflow_status == WorkflowStatus.READY_TO_SAVE.value
    assert response.completed_node == WorkflowNode.SAVE.value
    assert orchestrator.goal_agent.run.await_count == 1
    assert orchestrator.scheduler_agent.build_schedule.call_count == 1
    assert orchestrator.plan_agent.run.await_count == 2
    assert orchestrator.plan_validator_agent.validate.call_count == 1
    assert store.state is not None
    assert store.state.node_attempts[WorkflowNode.PLAN.value] == 2
    assert (WorkflowNode.REPLAN, "SKIPPED", 0) in store.events
    assert store.events[-1][:2] == (WorkflowNode.SAVE, "PAUSED")


def test_ready_workflow_replay_returns_checkpointed_plan() -> None:
    store = MemoryCheckpointStore()
    orchestrator = StatefulStudyOrchestrator(store)
    configure_agents(orchestrator)
    request = goal(uuid4())

    first = asyncio.run(orchestrator.run_study_plan_v2(request))
    second = asyncio.run(orchestrator.run_study_plan_v2(request))

    assert second.model_dump() == first.model_dump()
    assert orchestrator.goal_agent.run.await_count == 1
    assert orchestrator.plan_agent.run.await_count == 1



def test_checkpoint_feature_flag_restores_ephemeral_path(monkeypatch) -> None:
    monkeypatch.setenv("LEARNFLOW_WORKFLOW_CHECKPOINTS_ENABLED", "false")
    store = MemoryCheckpointStore()
    orchestrator = StatefulStudyOrchestrator(store)
    configure_agents(orchestrator)

    response = asyncio.run(orchestrator.run_study_plan_v2(goal(uuid4())))

    assert response.workflow_id is None
    assert response.workflow_status is None
    assert store.state is None

def test_fingerprint_excludes_workflow_id_but_detects_content_change() -> None:
    first = goal(uuid4())
    same_request = goal(uuid4())
    changed = goal(uuid4(), goal_text="Learn Python")

    assert workflow_request_fingerprint(first) == workflow_request_fingerprint(same_request)
    assert workflow_request_fingerprint(first) != workflow_request_fingerprint(changed)


def test_state_checksum_is_deterministic_and_unknown_version_is_rejected() -> None:
    request = goal(uuid4())
    state = StudyPlanState(
        workflow_id=request.workflow_id,
        request_fingerprint=workflow_request_fingerprint(request),
        goal=request,
    )

    assert state_checksum(state) == state_checksum(state.model_dump(mode="json"))
    payload = state.model_dump(mode="json")
    payload["schema_version"] = 2
    with pytest.raises(ValidationError, match="unsupported workflow schema version"):
        StudyPlanState.model_validate(payload)


def test_invalid_plan_is_repaired_and_revalidated_before_save() -> None:
    store = MemoryCheckpointStore()
    orchestrator = StatefulStudyOrchestrator(store)
    configure_agents(orchestrator)
    orchestrator.plan_validator_agent.validate = MagicMock(
        side_effect=[invalid_report(), PlanValidationReport(is_valid=True)]
    )

    response = asyncio.run(orchestrator.run_study_plan_v2(goal(uuid4())))

    assert response.workflow_status == WorkflowStatus.READY_TO_SAVE.value
    assert response.validation_report is not None and response.validation_report.is_valid
    assert orchestrator.replan_agent.repair_validation.await_count == 1
    assert store.state is not None
    assert store.state.plan_revision == 1
    assert store.state.validated_revision == 1
    assert store.state.node_attempts[WorkflowNode.VALIDATE.value] == 2
    assert store.state.node_attempts[WorkflowNode.REPLAN.value] == 1


def test_resume_validates_repaired_revision_before_another_repair() -> None:
    store = MemoryCheckpointStore()
    orchestrator = StatefulStudyOrchestrator(store)
    configure_agents(orchestrator)
    orchestrator.plan_validator_agent.validate = MagicMock(
        side_effect=[invalid_report(), RuntimeError("validator unavailable"), PlanValidationReport(is_valid=True)]
    )
    request = goal(uuid4())

    with pytest.raises(RuntimeError, match="validator unavailable"):
        asyncio.run(orchestrator.run_study_plan_v2(request))

    response = asyncio.run(orchestrator.run_study_plan_v2(request))

    assert response.workflow_status == WorkflowStatus.READY_TO_SAVE.value
    assert orchestrator.replan_agent.repair_validation.await_count == 1
    assert orchestrator.plan_validator_agent.validate.call_count == 3
    assert store.state is not None
    assert store.state.plan_revision == store.state.validated_revision == 1


def test_repeated_repair_fails_without_entering_save() -> None:
    store = MemoryCheckpointStore()
    orchestrator = StatefulStudyOrchestrator(store)
    configure_agents(orchestrator)
    orchestrator.plan_validator_agent.validate = MagicMock(return_value=invalid_report())
    orchestrator.replan_agent.repair_validation = AsyncMock(return_value=generated_plan())

    with pytest.raises(WorkflowRepairStalled, match="repeated"):
        asyncio.run(orchestrator.run_study_plan_v2(goal(uuid4())))

    assert store.state is not None and store.state.status == WorkflowStatus.FAILED
    assert not any(node == WorkflowNode.SAVE for node, _, _ in store.events)


def test_invalid_plan_exhausts_exactly_two_repairs() -> None:
    store = MemoryCheckpointStore()
    orchestrator = StatefulStudyOrchestrator(store)
    configure_agents(orchestrator)
    orchestrator.plan_validator_agent.validate = MagicMock(return_value=invalid_report())
    orchestrator.replan_agent.repair_validation = AsyncMock(
        side_effect=[revised_plan("repair-one"), revised_plan("repair-two")]
    )

    with pytest.raises(WorkflowValidationExhausted, match="2 automatic repair attempts"):
        asyncio.run(orchestrator.run_study_plan_v2(goal(uuid4())))

    assert orchestrator.replan_agent.repair_validation.await_count == 2
    assert store.state is not None
    assert store.state.status == WorkflowStatus.FAILED
    assert store.state.plan_revision == 2
    assert store.state.validated_revision == 2
    assert not any(node == WorkflowNode.SAVE for node, _, _ in store.events)


def test_validation_repair_rules_fix_coverage_repetition_and_load_without_shape_drift() -> None:
    plan = generated_plan()
    second_day = plan.days[0].model_copy(deep=True)
    second_day.day_index = 2
    second_day.date = plan.days[0].date + timedelta(days=1)
    plan.days.append(second_day)
    plan.end_date = second_day.date
    for day in plan.days:
        day.estimated_minutes = 240
        day.topic_ids = []
    blueprint = GoalPlanStructure(
        topics=[
            GoalTopic(id="java", name="Java", description="Core Java", order=1),
            GoalTopic(id="spring", name="Spring", description="Spring Boot", order=2),
        ]
    )
    report = PlanValidationReport(
        is_valid=False,
        issues=[
            {"code": "low_topic_coverage", "message": "coverage", "severity": "error"},
            {"code": "high_repetition", "message": "repeat", "severity": "error"},
            {"code": "unbalanced_load", "message": "load", "severity": "error"},
        ],
    )
    request = goal()

    repaired = asyncio.run(
        ReplanAgent().repair_validation(plan, request, report, goal_structure=blueprint)
    )
    validated = PlanValidatorAgent().validate(repaired, goal=request, goal_structure=blueprint)

    assert validated.is_valid
    assert [(day.day_index, day.date) for day in repaired.days] == [
        (day.day_index, day.date) for day in plan.days
    ]
    assert all(day.estimated_minutes == 60 for day in repaired.days)
