package com.learnflow.service;

import com.learnflow.config.LearnFlowTaskProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AsyncTaskLeaseService {

    private final JdbcTemplate jdbcTemplate;
    private final LearnFlowTaskProperties properties;

    public AsyncTaskLeaseService(JdbcTemplate jdbcTemplate, LearnFlowTaskProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional
    public Optional<UUID> claimNext(String workerId) {
        recoverExpiredAndTimedOut();
        List<UUID> ids = jdbcTemplate.query(
                """
                WITH candidate AS (
                    SELECT id
                    FROM async_task
                    WHERE status = 'PENDING'
                      AND cancel_requested_at IS NULL
                      AND pause_requested_at IS NULL
                      AND next_attempt_at <= CURRENT_TIMESTAMP
                      AND deadline_at > CURRENT_TIMESTAMP
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE async_task task
                   SET status = 'RUNNING',
                       attempt_count = attempt_count + 1,
                       lease_owner = ?,
                       lease_expires_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                       started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                       updated_at = CURRENT_TIMESTAMP
                  FROM candidate
                 WHERE task.id = candidate.id
                RETURNING task.id
                """,
                (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
                workerId,
                Math.max(1L, properties.getLeaseDuration().toMillis())
        );
        return ids.stream().findFirst();
    }

    private void recoverExpiredAndTimedOut() {
        jdbcTemplate.update(
                """
                UPDATE async_task
                       SET status = CASE
                           WHEN cancel_requested_at IS NOT NULL THEN 'CANCELLED'
                           WHEN pause_requested_at IS NOT NULL THEN 'PAUSED'
                           WHEN deadline_at <= CURRENT_TIMESTAMP OR attempt_count >= max_attempts THEN 'FAILED'
                           ELSE 'PENDING'
                       END,
                       request_payload = CASE
                           WHEN cancel_requested_at IS NOT NULL THEN NULL
                           ELSE request_payload
                       END,
                       error_code = CASE
                           WHEN cancel_requested_at IS NOT NULL OR pause_requested_at IS NOT NULL THEN NULL
                           WHEN deadline_at <= CURRENT_TIMESTAMP THEN 'TASK_DEADLINE_EXCEEDED'
                           WHEN attempt_count >= max_attempts THEN 'TASK_LEASE_EXHAUSTED'
                           ELSE 'TASK_LEASE_EXPIRED'
                       END,
                       error_summary = CASE
                           WHEN cancel_requested_at IS NOT NULL OR pause_requested_at IS NOT NULL THEN NULL
                           ELSE '任务租约过期，已由恢复器处理'
                       END,
                       next_attempt_at = CURRENT_TIMESTAMP,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       finished_at = CASE
                           WHEN cancel_requested_at IS NOT NULL OR deadline_at <= CURRENT_TIMESTAMP OR attempt_count >= max_attempts
                           THEN CURRENT_TIMESTAMP
                           ELSE NULL
                       END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'RUNNING'
                   AND lease_expires_at < CURRENT_TIMESTAMP
                """
        );
        jdbcTemplate.update(
                """
                UPDATE async_task
                   SET status = 'FAILED',
                       error_code = 'TASK_DEADLINE_EXCEEDED',
                       error_summary = '任务在执行前超过整体截止时间',
                       finished_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'PENDING'
                   AND pause_requested_at IS NULL
                   AND deadline_at <= CURRENT_TIMESTAMP
                """
        );
    }

    @Scheduled(cron = "${learnflow.tasks.cleanup-cron:0 41 3 * * *}")
    public void clearExpiredFailedPayloads() {
        jdbcTemplate.update(
                """
                UPDATE async_task
                   SET request_payload = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'FAILED'
                   AND request_payload IS NOT NULL
                   AND finished_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 millisecond')
                """,
                Math.max(1L, properties.getFailedPayloadRetention().toMillis())
        );
    }
}
