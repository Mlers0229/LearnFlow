package com.learnflow.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlanWorkflowStateService {

    private final JdbcTemplate jdbcTemplate;

    public PlanWorkflowStateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Complete the deterministic SAVE node after the business transaction has
     * produced an idempotent study_plan row. The Agent never receives write
     * access to study_plan or study_plan_day.
     */
    @Transactional
    public void markSaved(UUID workflowId, Long planId) {
        jdbcTemplate.update(
                """
                WITH updated AS (
                    UPDATE agent_workflow
                    SET status = 'SUCCEEDED',
                        current_node = 'SAVE',
                        checkpoint_sequence = checkpoint_sequence + 1,
                        result_resource_id = ?,
                        updated_at = CURRENT_TIMESTAMP,
                        expires_at = CURRENT_TIMESTAMP + INTERVAL '7 days'
                    WHERE workflow_id = ? AND status = 'READY_TO_SAVE'
                    RETURNING workflow_id, checkpoint_sequence, schema_version, state_json, state_checksum
                )
                INSERT INTO agent_workflow_checkpoint (
                    workflow_id, sequence, schema_version, node, outcome,
                    node_attempt, state_json, state_checksum
                )
                SELECT workflow_id, checkpoint_sequence, schema_version, 'SAVE', 'COMPLETED',
                       COALESCE((state_json -> 'node_attempts' ->> 'SAVE')::INTEGER, 0),
                       state_json, state_checksum
                FROM updated
                """,
                planId,
                workflowId
        );
    }

    @Transactional
    public void markCancelled(UUID workflowId) {
        jdbcTemplate.update(
                """
                WITH updated AS (
                    UPDATE agent_workflow
                    SET status = 'CANCELLED',
                        checkpoint_sequence = checkpoint_sequence + 1,
                        last_error_code = NULL,
                        updated_at = CURRENT_TIMESTAMP,
                        expires_at = CURRENT_TIMESTAMP + INTERVAL '7 days'
                    WHERE workflow_id = ? AND status NOT IN ('SUCCEEDED', 'CANCELLED')
                    RETURNING workflow_id, checkpoint_sequence, schema_version, current_node,
                              state_json, state_checksum
                )
                INSERT INTO agent_workflow_checkpoint (
                    workflow_id, sequence, schema_version, node, outcome,
                    node_attempt, state_json, state_checksum
                )
                SELECT workflow_id, checkpoint_sequence, schema_version, current_node, 'CANCELLED',
                       COALESCE((state_json -> 'node_attempts' ->> current_node)::INTEGER, 0),
                       state_json, state_checksum
                FROM updated
                """,
                workflowId
        );
    }

    @Transactional
    public void markPaused(UUID workflowId) {
        jdbcTemplate.update(
                """
                WITH updated AS (
                    UPDATE agent_workflow
                    SET status = 'PAUSED',
                        checkpoint_sequence = checkpoint_sequence + 1,
                        last_error_code = NULL,
                        updated_at = CURRENT_TIMESTAMP,
                        expires_at = CURRENT_TIMESTAMP + INTERVAL '7 days'
                    WHERE workflow_id = ? AND status NOT IN ('PAUSED', 'SUCCEEDED', 'CANCELLED')
                    RETURNING workflow_id, checkpoint_sequence, schema_version, current_node,
                              state_json, state_checksum
                )
                INSERT INTO agent_workflow_checkpoint (
                    workflow_id, sequence, schema_version, node, outcome,
                    node_attempt, state_json, state_checksum
                )
                SELECT workflow_id, checkpoint_sequence, schema_version, current_node, 'PAUSED',
                       COALESCE((state_json -> 'node_attempts' ->> current_node)::INTEGER, 0),
                       state_json, state_checksum
                FROM updated
                """,
                workflowId
        );
    }

    @Transactional
    public void markResumed(UUID workflowId) {
        jdbcTemplate.update(
                """
                WITH updated AS (
                    UPDATE agent_workflow
                    SET status = 'RUNNING',
                        checkpoint_sequence = checkpoint_sequence + 1,
                        last_error_code = NULL,
                        updated_at = CURRENT_TIMESTAMP,
                        expires_at = CURRENT_TIMESTAMP + INTERVAL '7 days'
                    WHERE workflow_id = ? AND status = 'PAUSED'
                    RETURNING workflow_id, checkpoint_sequence, schema_version, current_node,
                              state_json, state_checksum
                )
                INSERT INTO agent_workflow_checkpoint (
                    workflow_id, sequence, schema_version, node, outcome,
                    node_attempt, state_json, state_checksum
                )
                SELECT workflow_id, checkpoint_sequence, schema_version, current_node, 'RESUMED',
                       COALESCE((state_json -> 'node_attempts' ->> current_node)::INTEGER, 0),
                       state_json, state_checksum
                FROM updated
                """,
                workflowId
        );
    }
}
