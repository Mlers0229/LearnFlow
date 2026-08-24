package com.learnflow.service;

import com.learnflow.config.LearnFlowPrivacyProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PrivacyRequestStore {
    private final JdbcTemplate jdbc;
    private final LearnFlowPrivacyProperties properties;

    public PrivacyRequestStore(JdbcTemplate jdbc, LearnFlowPrivacyProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional
    public PrivacyRequestRecord createOrGet(UUID id, long userId, String subjectHash, String type, String fingerprint) {
        jdbc.update("""
                INSERT INTO privacy_request (
                    id, user_id, subject_ref_hash, request_type, request_fingerprint,
                    status, max_attempts, next_attempt_at
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, request_type, request_fingerprint) DO NOTHING
                """, id, userId, subjectHash, type, fingerprint, boundedMaxAttempts());
        return jdbc.query("""
                        SELECT * FROM privacy_request
                        WHERE user_id = ? AND request_type = ? AND request_fingerprint = ?
                        """, this::map, userId, type, fingerprint).stream()
                .findFirst().orElseThrow(() -> new IllegalStateException("Privacy request was not persisted"));
    }

    @Transactional
    public PrivacyRequestRecord createOrGetActiveErasure(UUID id, long userId, String subjectHash,
                                                         String fingerprint) {
        jdbc.update("""
                INSERT INTO privacy_request (
                    id, user_id, subject_ref_hash, request_type, request_fingerprint,
                    status, max_attempts, next_attempt_at
                ) VALUES (?, ?, ?, 'ERASURE', ?, 'PENDING', ?, CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """, id, userId, subjectHash, fingerprint, boundedMaxAttempts());
        return findActiveErasure(userId)
                .orElseThrow(() -> new IllegalStateException("Active erasure request was not persisted"));
    }

    public Optional<PrivacyRequestRecord> findOwned(UUID id, long userId) {
        return jdbc.query("SELECT * FROM privacy_request WHERE id = ? AND user_id = ?", this::map, id, userId)
                .stream().findFirst();
    }

    public Optional<PrivacyRequestRecord> findActiveErasure(long userId) {
        return jdbc.query("""
                        SELECT * FROM privacy_request
                        WHERE user_id = ? AND request_type = 'ERASURE' AND status IN ('PENDING', 'RUNNING')
                        ORDER BY created_at DESC LIMIT 1
                        """, this::map, userId).stream().findFirst();
    }

    public int cancelPendingExportsForErasure(long userId) {
        return jdbc.update("""
                UPDATE privacy_request
                SET status = 'FAILED', error_code = 'ERASURE_REQUESTED',
                    completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND request_type = 'EXPORT' AND status = 'PENDING'
                """, userId);
    }

    public boolean hasRunningExport(long userId) {
        Boolean running = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM privacy_request
                    WHERE user_id = ? AND request_type = 'EXPORT' AND status = 'RUNNING'
                )
                """, Boolean.class, userId);
        return Boolean.TRUE.equals(running);
    }

    @Transactional
    public Optional<PrivacyRequestRecord> claimNext(String workerId) {
        jdbc.update("""
                UPDATE privacy_request
                SET status = 'PENDING', lease_owner = NULL, lease_expires_at = NULL,
                    next_attempt_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE status = 'RUNNING' AND lease_expires_at < CURRENT_TIMESTAMP
                  AND attempt_count < max_attempts
                """);
        jdbc.update("""
                UPDATE privacy_request
                SET status = 'FAILED', error_code = 'ATTEMPTS_EXHAUSTED',
                    lease_owner = NULL, lease_expires_at = NULL,
                    completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE status = 'RUNNING' AND lease_expires_at < CURRENT_TIMESTAMP
                  AND attempt_count >= max_attempts
                """);
        return jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM privacy_request
                    WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE privacy_request request
                SET status = 'RUNNING', attempt_count = request.attempt_count + 1,
                    lease_owner = ?, lease_expires_at = ?,
                    started_at = COALESCE(request.started_at, CURRENT_TIMESTAMP),
                    error_code = NULL, updated_at = CURRENT_TIMESTAMP
                FROM candidate
                WHERE request.id = candidate.id
                RETURNING request.*
                """, this::map, workerId, now().plus(properties.getLeaseDuration())).stream().findFirst();
    }

    public List<String> objectKeysForErasure(long userId) {
        return jdbc.queryForList("""
                SELECT DISTINCT object_key FROM (
                    SELECT ingestion.object_key AS object_key
                    FROM resource_ingestion ingestion
                    JOIN resource_bank resource ON resource.id = ingestion.resource_id
                    WHERE resource.uploader_user_id = ? AND ingestion.object_key IS NOT NULL
                    UNION ALL
                    SELECT artifact_object_key AS object_key
                    FROM privacy_request
                    WHERE user_id = ? AND request_type = 'EXPORT'
                      AND artifact_object_key IS NOT NULL AND artifact_deleted_at IS NULL
                ) objects
                """, String.class, userId, userId);
    }

    public void completeExport(UUID id, String objectKey, String sha256, long size, OffsetDateTime expiresAt) {
        int updated = jdbc.update("""
                UPDATE privacy_request
                SET status = 'SUCCEEDED', artifact_object_key = ?, artifact_sha256 = ?,
                    artifact_size_bytes = ?, artifact_expires_at = ?,
                    lease_owner = NULL, lease_expires_at = NULL, error_code = NULL,
                    completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND request_type = 'EXPORT' AND status = 'RUNNING'
                """, objectKey, sha256, size, expiresAt, id);
        if (updated != 1) throw new IllegalStateException("Export request is no longer running");
    }

    public void failOrRetry(PrivacyRequestRecord request, Throwable failure) {
        String errorCode = boundedErrorCode(failure);
        if (request.attemptCount() >= request.maxAttempts()) {
            jdbc.update("""
                    UPDATE privacy_request
                    SET status = 'FAILED', error_code = ?, lease_owner = NULL, lease_expires_at = NULL,
                        completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'RUNNING'
                    """, errorCode, request.id());
            return;
        }
        long multiplier = 1L << Math.min(6, Math.max(0, request.attemptCount() - 1));
        Duration delay = properties.getRetryBaseDelay().multipliedBy(multiplier);
        jdbc.update("""
                UPDATE privacy_request
                SET status = 'PENDING', error_code = ?, next_attempt_at = ?,
                    lease_owner = NULL, lease_expires_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'RUNNING'
                """, errorCode, now().plus(delay), request.id());
    }

    public List<ExpiredArtifact> findExpiredArtifacts(int limit) {
        return jdbc.query("""
                SELECT id, artifact_object_key FROM privacy_request
                WHERE artifact_object_key IS NOT NULL AND artifact_deleted_at IS NULL
                  AND artifact_expires_at <= CURRENT_TIMESTAMP
                ORDER BY artifact_expires_at LIMIT ?
                """, (rs, row) -> new ExpiredArtifact(
                        rs.getObject("id", UUID.class), rs.getString("artifact_object_key")), limit);
    }

    public void markArtifactDeleted(UUID id, String objectKey) {
        jdbc.update("""
                UPDATE privacy_request
                SET artifact_object_key = NULL, artifact_sha256 = NULL, artifact_size_bytes = NULL,
                    artifact_expires_at = NULL, artifact_deleted_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND artifact_object_key = ?
                """, id, objectKey);
    }

    public int purgeCompletedBefore(OffsetDateTime cutoff) {
        return jdbc.update("""
                DELETE FROM privacy_request
                WHERE status IN ('SUCCEEDED', 'FAILED') AND completed_at < ?
                  AND artifact_object_key IS NULL
                """, cutoff);
    }

    private PrivacyRequestRecord map(ResultSet rs, int row) throws SQLException {
        return new PrivacyRequestRecord(
                rs.getObject("id", UUID.class),
                (Long) rs.getObject("user_id"),
                rs.getString("request_type"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                rs.getInt("max_attempts"),
                rs.getString("artifact_object_key"),
                rs.getString("artifact_sha256"),
                (Long) rs.getObject("artifact_size_bytes"),
                rs.getObject("artifact_expires_at", OffsetDateTime.class),
                rs.getObject("artifact_deleted_at", OffsetDateTime.class),
                rs.getString("error_code"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class)
        );
    }

    private int boundedMaxAttempts() { return Math.max(1, Math.min(10, properties.getMaxAttempts())); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private String boundedErrorCode(Throwable failure) {
        String value = failure == null ? "UNKNOWN" : failure.getClass().getSimpleName().toUpperCase();
        value = value.replaceAll("[^A-Z0-9_]", "_");
        return value.substring(0, Math.min(64, value.length()));
    }

    public record PrivacyRequestRecord(
            UUID id, Long userId, String type, String status, int attemptCount, int maxAttempts,
            String artifactObjectKey, String artifactSha256, Long artifactSizeBytes,
            OffsetDateTime artifactExpiresAt, OffsetDateTime artifactDeletedAt, String errorCode,
            OffsetDateTime createdAt, OffsetDateTime completedAt
    ) {}

    public record ExpiredArtifact(UUID id, String objectKey) {}
}
