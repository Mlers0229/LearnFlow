package com.learnflow.integration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveLearningMigrationContractTest {

    @Test
    void v17DefinesStableAssignmentsMinimalDecisionsAndAgentIsolation() throws Exception {
        String migration = resource("db/migration/V17__add_adaptive_learning_policy.sql").toLowerCase();

        assertThat(migration).contains("create table adaptive_policy_assignment");
        assertThat(migration).contains("unique (user_id, experiment_key)");
        assertThat(migration).contains("create table adaptive_decision");
        assertThat(migration).contains("decision_key char(64) not null unique");
        assertThat(migration).contains("revoke all on adaptive_policy_assignment, adaptive_decision from learnflow_agent");
        assertThat(migration).contains("add column adaptation_policy_version");
        assertThat(migration).doesNotContain("question text");
        assertThat(migration).doesNotContain("answer text");
        assertThat(migration).doesNotContain("prompt text");
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = AdaptiveLearningMigrationContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as("migration resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
