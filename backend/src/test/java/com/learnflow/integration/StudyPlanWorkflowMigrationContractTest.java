package com.learnflow.integration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StudyPlanWorkflowMigrationContractTest {

    @Test
    void v14DefinesVersionedCheckpointsRetentionAndLeastPrivilege() throws Exception {
        String migration = resource("db/migration/V14__add_study_plan_workflow_checkpoints.sql")
                .toLowerCase();

        assertThat(migration).contains("create table agent_workflow");
        assertThat(migration).contains("create table agent_workflow_checkpoint");
        assertThat(migration).contains("schema_version = 1");
        assertThat(migration).contains("references async_task(id) on delete cascade");
        assertThat(migration).contains("state_checksum char(64)");
        assertThat(migration).contains("current_timestamp + interval '7 days'");
        assertThat(migration).contains("grant select, insert, update, delete on agent_workflow to learnflow_agent");
        assertThat(migration).contains("grant select, insert on agent_workflow_checkpoint to learnflow_agent");
        assertThat(migration).doesNotContain("grant insert on study_plan to learnflow_agent");
    }

    @Test
    void v15AddsCooperativePauseAndAuditedResumeWithoutChangingPayloadRetention() throws Exception {
        String migration = resource("db/migration/V15__add_async_task_pause_resume.sql")
                .toLowerCase();

        assertThat(migration).contains("add column pause_requested_at");
        assertThat(migration).contains("'paused'");
        assertThat(migration).contains("'resumed'");
        assertThat(migration).contains("where status = 'paused'");
        assertThat(migration).contains("grant select (id, status) on async_task to learnflow_agent");
        assertThat(migration).doesNotContain("grant update on async_task to learnflow_agent");
        assertThat(migration).doesNotContain("drop column request_payload");
        assertThat(migration).doesNotContain("update async_task set request_payload = null");
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = StudyPlanWorkflowMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(name)) {
            assertThat(input).as("migration resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
