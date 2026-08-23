from __future__ import annotations

import hashlib
import json
import re
from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field, model_validator

from app.models.goal import GoalPlanStructure, GoalRequest
from app.models.plan import LearningPhase, PlanResponse, PlanValidationReport, WeeklyPlan

WORKFLOW_SCHEMA_VERSION = 1


class WorkflowNode(StrEnum):
    GOAL = "GOAL"
    SCHEDULE = "SCHEDULE"
    PLAN = "PLAN"
    VALIDATE = "VALIDATE"
    REPLAN = "REPLAN"
    SAVE = "SAVE"


class WorkflowStatus(StrEnum):
    RUNNING = "RUNNING"
    PAUSED = "PAUSED"
    READY_TO_SAVE = "READY_TO_SAVE"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class StudyPlanState(BaseModel):
    """Versioned durable state for one asynchronous study-plan workflow."""

    schema_version: int = Field(default=WORKFLOW_SCHEMA_VERSION, frozen=True)
    workflow_id: UUID
    request_fingerprint: str = Field(pattern=r"^[0-9a-f]{64}$")
    trace_id: str | None = None
    status: WorkflowStatus = WorkflowStatus.RUNNING
    current_node: WorkflowNode = WorkflowNode.GOAL
    completed_nodes: list[WorkflowNode] = Field(default_factory=list)
    node_attempts: dict[str, int] = Field(default_factory=dict)
    goal: GoalRequest
    goal_blueprint: GoalPlanStructure | None = None
    phases: list[LearningPhase] = Field(default_factory=list)
    weeks: list[WeeklyPlan] = Field(default_factory=list)
    plan: PlanResponse | None = None
    validation_report: PlanValidationReport | None = None
    plan_revision: int = Field(default=0, ge=0)
    validated_revision: int = Field(default=-1, ge=-1)
    plan_fingerprints: list[str] = Field(default_factory=list)
    last_error_code: str | None = Field(default=None, max_length=64)

    @model_validator(mode="after")
    def validate_version_and_progress(self) -> StudyPlanState:
        if self.schema_version != WORKFLOW_SCHEMA_VERSION:
            raise ValueError(f"unsupported workflow schema version: {self.schema_version}")
        if len(set(self.completed_nodes)) != len(self.completed_nodes):
            raise ValueError("completed_nodes must not contain duplicates")
        if any(value < 0 for value in self.node_attempts.values()):
            raise ValueError("node attempts must be non-negative")
        if self.validated_revision > self.plan_revision:
            raise ValueError("validated_revision must not exceed plan_revision")
        if any(not re.fullmatch(r"[0-9a-f]{64}", value) for value in self.plan_fingerprints):
            raise ValueError("plan_fingerprints must contain SHA-256 values")
        if self.status in {WorkflowStatus.READY_TO_SAVE, WorkflowStatus.SUCCEEDED} and self.plan is None:
            raise ValueError("a save-ready workflow must contain a plan")
        return self

    def begin_node(self, node: WorkflowNode) -> int:
        self.status = WorkflowStatus.RUNNING
        self.current_node = node
        self.last_error_code = None
        key = node.value
        self.node_attempts[key] = self.node_attempts.get(key, 0) + 1
        return self.node_attempts[key]

    def complete_node(self, node: WorkflowNode) -> None:
        if node not in self.completed_nodes:
            self.completed_nodes.append(node)

    def node_attempt(self, node: WorkflowNode) -> int:
        return self.node_attempts.get(node.value, 0)


def workflow_request_fingerprint(goal: GoalRequest) -> str:
    payload = goal.model_dump(mode="json", exclude={"workflow_id"})
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def canonical_state_payload(state: StudyPlanState | dict[str, Any]) -> str:
    payload = state.model_dump(mode="json") if isinstance(state, StudyPlanState) else state
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def state_checksum(state: StudyPlanState | dict[str, Any]) -> str:
    return hashlib.sha256(canonical_state_payload(state).encode("utf-8")).hexdigest()
