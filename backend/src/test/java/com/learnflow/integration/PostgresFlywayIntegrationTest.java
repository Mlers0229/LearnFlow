package com.learnflow.integration;

import com.learnflow.service.AsyncTaskLeaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

@SpringBootTest(properties = {
        "learnflow.seed-resources=false",
        "spring.task.scheduling.enabled=false",
        "learnflow.auth.jwt-secret=0123456789abcdef0123456789abcdef"
})
@Testcontainers(disabledWithoutDocker = true)
class PostgresFlywayIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg15").asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void flywayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AsyncTaskLeaseService leaseService;

    @Test
    void emptyDatabaseMigratesAndJpaMappingsValidate() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class
        );
        Integer resetTable = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'password_reset_token'",
                Integer.class
        );
        Integer asyncTaskTable = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = 'async_task'",
                Integer.class
        );
        Integer sourceTaskColumn = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'study_plan' and column_name = 'source_task_id'",
                Integer.class
        );
        Integer traceContextColumns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'async_task' and column_name in ('traceparent', 'request_id')",
                Integer.class
        );
        String progressDataType = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns where table_schema = 'public' and table_name = 'async_task' and column_name = 'progress'",
                String.class
        );
        Integer hashVarcharColumns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' and ((table_name = 'resource_chunk' and column_name = 'content_hash') or (table_name = 'resource_ingestion' and column_name = 'content_sha256')) and data_type = 'character varying'",
                Integer.class
        );

        Integer ingestionTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('resource_ingestion', 'resource_chunk', 'resource_ingestion_chunk')",
                Integer.class
        );
        Integer ingestionColumns = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'resource_bank' and column_name in ('source_type', 'ingestion_status', 'current_ingestion_id')",
                Integer.class
        );
        Integer vectorExtension = jdbcTemplate.queryForObject(
                "select count(*) from pg_extension where extname = 'vector'",
                Integer.class
        );
        Integer embeddingTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('embedding_model_version', 'resource_chunk_embedding')",
                Integer.class
        );
        Integer hnswIndexes = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' and indexname = 'idx_resource_chunk_embedding_hnsw' and indexdef ilike '%using hnsw%'",
                Integer.class
        );

        assertThat(migrationCount).isGreaterThanOrEqualTo(12);
        assertThat(resetTable).isEqualTo(1);
        assertThat(asyncTaskTable).isEqualTo(1);
        assertThat(sourceTaskColumn).isEqualTo(1);
        assertThat(traceContextColumns).isEqualTo(2);
        assertThat(progressDataType).isEqualTo("integer");
        assertThat(hashVarcharColumns).isEqualTo(2);
        assertThat(ingestionTables).isEqualTo(3);
        assertThat(ingestionColumns).isEqualTo(3);
        assertThat(vectorExtension).isEqualTo(1);
        assertThat(embeddingTables).isEqualTo(2);
        assertThat(hnswIndexes).isEqualTo(1);
    }

    @Test
    void expiredWorkerLeaseIsRecoveredAndClaimedOnce() {
        long userId = 900001L;
        UUID taskId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, username, password_hash, role, status) values (?, ?, ?, 'student', 'ACTIVE')",
                userId,
                "queue-test-user",
                "not-a-real-password-hash"
        );
        jdbcTemplate.update(
                """
                insert into async_task (
                    id, task_type, owner_user_id, idempotency_key, request_fingerprint, status, progress,
                    request_payload, attempt_count, max_attempts, next_attempt_at,
                    lease_owner, lease_expires_at, deadline_at, created_at, updated_at
                ) values (?, 'PLAN_GENERATION', ?, 'lease-test', 'fingerprint', 'RUNNING', 10,
                    '{}', 1, 3, CURRENT_TIMESTAMP, 'dead-worker', CURRENT_TIMESTAMP - INTERVAL '1 minute',
                    CURRENT_TIMESTAMP + INTERVAL '10 minutes', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                taskId,
                userId
        );

        assertThat(leaseService.claimNext("replacement-worker")).contains(taskId);
        assertThat(leaseService.claimNext("other-worker")).isEmpty();

        String status = jdbcTemplate.queryForObject(
                "select status from async_task where id = ?",
                String.class,
                taskId
        );
        Integer attempts = jdbcTemplate.queryForObject(
                "select attempt_count from async_task where id = ?",
                Integer.class,
                taskId
        );
        assertThat(status).isEqualTo("RUNNING");
        assertThat(attempts).isEqualTo(2);
    }
}
