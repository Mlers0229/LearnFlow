package com.learnflow.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodically snapshots operational queue state into bounded-cardinality gauges.
 * Database work is performed on the scheduler, never on a Prometheus scrape thread.
 */
@Component
public class OperationalMetrics {

    private static final Logger log = LoggerFactory.getLogger(OperationalMetrics.class);
    private static final String[] TASK_STATUSES = {
            "PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"
    };

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, AtomicLong> taskCounts = new LinkedHashMap<>();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();
    private final AtomicLong expiredLeases = new AtomicLong();
    private final AtomicLong snapshotFresh = new AtomicLong();

    public OperationalMetrics(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        for (String status : TASK_STATUSES) {
            AtomicLong value = new AtomicLong();
            taskCounts.put(status, value);
            Gauge.builder("learnflow.async.queue.tasks", value, AtomicLong::doubleValue)
                    .description("Persisted asynchronous tasks by lifecycle state")
                    .tag("status", status.toLowerCase())
                    .register(meterRegistry);
        }
        Gauge.builder("learnflow.async.queue.oldest.pending.age", oldestPendingAgeSeconds, AtomicLong::doubleValue)
                .description("Age in seconds of the oldest pending asynchronous task")
                .baseUnit("seconds")
                .register(meterRegistry);
        Gauge.builder("learnflow.async.queue.expired.leases", expiredLeases, AtomicLong::doubleValue)
                .description("Running tasks whose worker lease has expired")
                .register(meterRegistry);
        Gauge.builder("learnflow.async.queue.metrics.fresh", snapshotFresh, AtomicLong::doubleValue)
                .description("Whether the latest queue metrics snapshot succeeded")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${learnflow.observability.queue-metrics-interval-ms:15000}")
    public void refreshQueueMetrics() {
        try {
            Map<String, Long> current = new LinkedHashMap<>();
            jdbcTemplate.query(
                    "SELECT status, COUNT(*) FROM async_task GROUP BY status",
                    (RowCallbackHandler) resultSet ->
                            current.put(resultSet.getString(1), resultSet.getLong(2))
            );
            taskCounts.forEach((status, gauge) -> gauge.set(current.getOrDefault(status, 0L)));
            oldestPendingAgeSeconds.set(queryLong("""
                    SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN(created_at)))::bigint, 0)
                      FROM async_task
                     WHERE status = 'PENDING'
                    """));
            expiredLeases.set(queryLong("""
                    SELECT COUNT(*)
                      FROM async_task
                     WHERE status = 'RUNNING'
                       AND lease_expires_at < CURRENT_TIMESTAMP
                    """));
            snapshotFresh.set(1);
        } catch (RuntimeException failure) {
            snapshotFresh.set(0);
            log.warn("Operational queue metric refresh failed errorType={}", failure.getClass().getSimpleName());
        }
    }

    private long queryLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : Math.max(0, value);
    }
}
