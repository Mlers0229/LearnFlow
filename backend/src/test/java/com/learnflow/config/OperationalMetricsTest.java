package com.learnflow.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalMetricsTest {

    @Test
    void snapshotsBoundedQueueStateAndFreshness() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ResultSet pending = row("PENDING", 4L);
        ResultSet failed = row("FAILED", 2L);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(pending);
            handler.processRow(failed);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class)))
                .thenReturn(75L, 1L);

        OperationalMetrics metrics = new OperationalMetrics(jdbcTemplate, registry);
        metrics.refreshQueueMetrics();

        assertThat(gauge(registry, "learnflow.async.queue.tasks", "status", "pending")).isEqualTo(4);
        assertThat(gauge(registry, "learnflow.async.queue.tasks", "status", "failed")).isEqualTo(2);
        assertThat(gauge(registry, "learnflow.async.queue.tasks", "status", "running")).isZero();
        assertThat(gauge(registry, "learnflow.async.queue.oldest.pending.age", null, null)).isEqualTo(75);
        assertThat(gauge(registry, "learnflow.async.queue.expired.leases", null, null)).isEqualTo(1);
        assertThat(gauge(registry, "learnflow.async.queue.metrics.fresh", null, null)).isEqualTo(1);
    }

    @Test
    void marksSnapshotStaleWhenDatabaseCollectionFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        doAnswer(invocation -> {
            throw new IllegalStateException("database unavailable");
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));

        OperationalMetrics metrics = new OperationalMetrics(jdbcTemplate, registry);
        metrics.refreshQueueMetrics();

        assertThat(gauge(registry, "learnflow.async.queue.metrics.fresh", null, null)).isZero();
    }

    private static ResultSet row(String status, long count) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(1)).thenReturn(status);
        when(resultSet.getLong(2)).thenReturn(count);
        return resultSet;
    }

    private static double gauge(
            SimpleMeterRegistry registry,
            String name,
            String tagName,
            String tagValue
    ) {
        if (tagName == null) {
            return registry.get(name).gauge().value();
        }
        return registry.get(name).tag(tagName, tagValue).gauge().value();
    }
}
