package com.learnflow.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PrivacyErasurePersistence {
    private final JdbcTemplate jdbc;

    public PrivacyErasurePersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void erase(UUID requestId, long userId) {
        List<String> usernames = jdbc.queryForList("SELECT username FROM app_user WHERE id = ?", String.class, userId);
        if (usernames.isEmpty()) {
            completeRequest(requestId);
            return;
        }
        String username = usernames.get(0);

        jdbc.update("""
                DELETE FROM user_resource_feedback
                WHERE user_id = ? OR resource_bank_id IN (
                    SELECT id FROM resource_bank WHERE uploader_user_id = ?
                )
                """, userId, userId);
        jdbc.update("""
                DELETE FROM resource_ingestion_chunk
                WHERE ingestion_id IN (
                    SELECT ingestion.id FROM resource_ingestion ingestion
                    JOIN resource_bank resource ON resource.id = ingestion.resource_id
                    WHERE resource.uploader_user_id = ?
                )
                """, userId);
        jdbc.update("UPDATE resource_bank SET current_ingestion_id = NULL WHERE uploader_user_id = ?", userId);
        jdbc.update("""
                DELETE FROM resource_chunk WHERE resource_id IN (
                    SELECT id FROM resource_bank WHERE uploader_user_id = ?
                )
                """, userId);
        jdbc.update("""
                DELETE FROM resource_ingestion WHERE resource_id IN (
                    SELECT id FROM resource_bank WHERE uploader_user_id = ?
                )
                """, userId);
        jdbc.update("DELETE FROM resource_bank WHERE uploader_user_id = ?", userId);

        jdbc.update("DELETE FROM mastery_profile WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM learning_event WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM adaptive_decision WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM adaptive_policy_assignment WHERE user_id = ?", userId);

        jdbc.update("""
                DELETE FROM exercise_record
                WHERE user_id = ? OR plan_day_id IN (
                    SELECT day.id FROM study_plan_day day
                    JOIN study_plan plan ON plan.id = day.plan_id
                    WHERE plan.user_id = ?
                )
                """, userId, userId);
        jdbc.update("UPDATE study_plan SET source_task_id = NULL WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM async_task WHERE owner_user_id = ?", userId);
        jdbc.update("""
                DELETE FROM study_plan_day WHERE plan_id IN (
                    SELECT id FROM study_plan WHERE user_id = ?
                )
                """, userId);
        jdbc.update("DELETE FROM study_plan WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM password_reset_token WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM refresh_token_session WHERE user_id = ?", userId);

        jdbc.update("""
                UPDATE admin_audit_log
                SET operator = 'erased-subject', target_id = NULL,
                    detail = CONCAT('privacy_erasure_request=', ?)
                WHERE operator = ? OR (target_type = 'USER' AND target_id = ?)
                """, requestId.toString(), username, userId);
        jdbc.update("""
                UPDATE privacy_request
                SET artifact_object_key = NULL, artifact_sha256 = NULL, artifact_size_bytes = NULL,
                    artifact_expires_at = NULL, artifact_deleted_at = COALESCE(artifact_deleted_at, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND request_type = 'EXPORT'
                """, userId);

        int deleted = jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        if (deleted != 1) throw new IllegalStateException("Erasure subject changed during processing");
        jdbc.update("""
                INSERT INTO admin_audit_log (type, operator, target_type, target_id, detail, created_at)
                VALUES ('ACCOUNT_ERASURE_COMPLETED', 'privacy-worker', 'PRIVACY_REQUEST', NULL, ?, CURRENT_TIMESTAMP)
                """, "request=" + requestId);
        completeRequest(requestId);
    }

    private void completeRequest(UUID requestId) {
        int updated = jdbc.update("""
                UPDATE privacy_request
                SET status = 'SUCCEEDED', user_id = NULL, lease_owner = NULL, lease_expires_at = NULL,
                    error_code = NULL, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND request_type = 'ERASURE' AND status = 'RUNNING'
                """, requestId);
        if (updated != 1) throw new IllegalStateException("Erasure request is no longer running");
    }
}
