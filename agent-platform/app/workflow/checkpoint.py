from __future__ import annotations

import json
from typing import Protocol
from uuid import UUID

from sqlalchemy import text
from sqlalchemy.orm import Session, sessionmaker

from app.db import SessionLocal
from app.models.workflow import (
    WORKFLOW_SCHEMA_VERSION,
    StudyPlanState,
    WorkflowNode,
    WorkflowStatus,
    canonical_state_payload,
    state_checksum,
)


class WorkflowStateError(RuntimeError):
    """Raised when a persisted workflow cannot be resumed safely."""


class CheckpointStore(Protocol):
    def load(self, workflow_id: UUID, request_fingerprint: str) -> StudyPlanState | None: ...

    def save(self, state: StudyPlanState, node: WorkflowNode, outcome: str) -> int: ...


class PostgresCheckpointStore:
    """Atomically upsert current state and append an immutable node checkpoint."""

    _OUTCOMES = {"STARTED", "COMPLETED", "SKIPPED", "FAILED", "PAUSED", "CANCELLED"}

    def __init__(self, sessions: sessionmaker[Session] = SessionLocal) -> None:
        self._sessions = sessions

    def load(self, workflow_id: UUID, request_fingerprint: str) -> StudyPlanState | None:
        with self._sessions() as db:
            row = db.execute(
                text(
                    """
                    SELECT schema_version, request_fingerprint, status, current_node,
                           state_json, state_checksum, last_error_code
                    FROM agent_workflow
                    WHERE workflow_id = :workflow_id
                    """
                ),
                {"workflow_id": workflow_id},
            ).mappings().one_or_none()
        if row is None:
            return None
        if int(row["schema_version"]) != WORKFLOW_SCHEMA_VERSION:
            raise WorkflowStateError("unsupported workflow schema version")
        if str(row["request_fingerprint"]) != request_fingerprint:
            raise WorkflowStateError("workflow request fingerprint mismatch")
        raw = row["state_json"]
        payload = json.loads(raw) if isinstance(raw, str) else raw
        if state_checksum(payload) != str(row["state_checksum"]):
            raise WorkflowStateError("workflow state checksum mismatch")
        try:
            state = StudyPlanState.model_validate(payload)
            state.status = WorkflowStatus(str(row["status"]))
            state.current_node = WorkflowNode(str(row["current_node"]))
            state.last_error_code = row["last_error_code"]
            return state
        except ValueError as exc:
            raise WorkflowStateError("workflow state is invalid") from exc

    def save(self, state: StudyPlanState, node: WorkflowNode, outcome: str) -> int:
        if outcome not in self._OUTCOMES:
            raise ValueError(f"unsupported checkpoint outcome: {outcome}")
        payload = canonical_state_payload(state)
        checksum = state_checksum(state)
        params = {
            "workflow_id": state.workflow_id,
            "schema_version": state.schema_version,
            "request_fingerprint": state.request_fingerprint,
            "status": state.status.value,
            "current_node": state.current_node.value,
            "state_json": payload,
            "state_checksum": checksum,
            "last_error_code": state.last_error_code,
        }
        with self._sessions.begin() as db:
            sequence = db.execute(
                text(
                    """
                    INSERT INTO agent_workflow (
                        workflow_id, schema_version, request_fingerprint, status, current_node,
                        checkpoint_sequence, state_json, state_checksum, last_error_code
                    )
                    SELECT
                        :workflow_id, :schema_version, :request_fingerprint, :status, :current_node,
                        1, CAST(:state_json AS jsonb), :state_checksum, :last_error_code
                    FROM async_task task
                    WHERE task.id = :workflow_id
                      AND task.status NOT IN ('PAUSED', 'CANCELLED')
                    ON CONFLICT (workflow_id) DO UPDATE SET
                        status = EXCLUDED.status,
                        current_node = EXCLUDED.current_node,
                        checkpoint_sequence = agent_workflow.checkpoint_sequence + 1,
                        state_json = EXCLUDED.state_json,
                        state_checksum = EXCLUDED.state_checksum,
                        last_error_code = EXCLUDED.last_error_code,
                        expires_at = CURRENT_TIMESTAMP + INTERVAL '7 days',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE agent_workflow.schema_version = EXCLUDED.schema_version
                      AND agent_workflow.request_fingerprint = EXCLUDED.request_fingerprint
                      AND agent_workflow.status NOT IN ('PAUSED', 'SUCCEEDED', 'CANCELLED')
                    RETURNING checkpoint_sequence
                    """
                ),
                params,
            ).scalar_one_or_none()
            if sequence is None:
                raise WorkflowStateError("workflow is terminal or incompatible")
            db.execute(
                text(
                    """
                    INSERT INTO agent_workflow_checkpoint (
                        workflow_id, sequence, schema_version, node, outcome,
                        node_attempt, state_json, state_checksum
                    ) VALUES (
                        :workflow_id, :sequence, :schema_version, :node, :outcome,
                        :node_attempt, CAST(:state_json AS jsonb), :state_checksum
                    )
                    """
                ),
                {
                    **params,
                    "sequence": sequence,
                    "node": node.value,
                    "outcome": outcome,
                    "node_attempt": state.node_attempt(node),
                },
            )
            return int(sequence)
