package com.learnflow.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseRoleMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg15").asCompatibleSubstituteFor("postgres")
    );

    @Test
    void migrationsGrantBackendAndAgentOnlyTheirRuntimePrivileges() throws Exception {
        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("create extension if not exists vector");
            statement.execute("create role learnflow_migrator login password 'migration-password'");
            statement.execute("create role learnflow_backend login password 'backend-password'");
            statement.execute("create role learnflow_agent login password 'agent-password'");
            statement.execute("grant connect on database " + POSTGRES.getDatabaseName()
                    + " to learnflow_migrator, learnflow_backend, learnflow_agent");
            statement.execute("grant usage, create on schema public to learnflow_migrator");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), "learnflow_migrator", "migration-password")
                .load()
                .migrate();

        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            assertThat(privilege(statement, "learnflow_backend", "app_user", "SELECT")).isTrue();
            assertThat(privilege(statement, "learnflow_backend", "app_user", "INSERT")).isTrue();
            assertThat(privilege(statement, "learnflow_backend", "async_task", "SELECT")).isTrue();
            assertThat(privilege(statement, "learnflow_backend", "async_task", "UPDATE")).isTrue();
            assertThat(privilege(statement, "learnflow_agent", "resource_bank", "SELECT")).isTrue();
            assertThat(privilege(statement, "learnflow_agent", "agent_call_log", "INSERT")).isTrue();
            assertThat(privilege(statement, "learnflow_agent", "agent_call_log", "DELETE")).isTrue();
            assertThat(privilege(statement, "learnflow_agent", "app_user", "SELECT")).isFalse();
            assertThat(privilege(statement, "learnflow_agent", "async_task", "SELECT")).isFalse();
            assertThat(privilege(statement, "learnflow_backend", "resource_chunk_embedding", "INSERT")).isTrue();
            assertThat(privilege(statement, "learnflow_agent", "resource_chunk_embedding", "SELECT")).isTrue();
            assertThat(privilege(statement, "learnflow_agent", "resource_chunk_embedding", "INSERT")).isFalse();
            assertThat(privilege(statement, "learnflow_agent", "embedding_model_version", "UPDATE")).isFalse();
            assertThat(indexExists(statement, "idx_study_plan_day_plan_index")).isTrue();
            assertThat(indexExists(statement, "idx_resource_chunk_embedding_hnsw")).isTrue();
        }
    }

    private boolean privilege(Statement statement, String role, String table, String privilege) throws Exception {
        try (ResultSet result = statement.executeQuery("select has_table_privilege('" + role + "', '" + table
                + "', '" + privilege + "')")) {
            return result.next() && result.getBoolean(1);
        }
    }

    private boolean indexExists(Statement statement, String index) throws Exception {
        try (ResultSet result = statement.executeQuery("select to_regclass('public." + index + "') is not null")) {
            return result.next() && result.getBoolean(1);
        }
    }
}
