package com.learnflow.integration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryMigrationContractTest {

    @Test
    void v16DefinesAppendOnlyReplayablePrivacyMinimizedMasterySchema() throws Exception {
        String migration = resource("db/migration/V16__add_learning_events_and_mastery_profiles.sql")
                .toLowerCase();

        assertThat(migration).contains("create table knowledge_point");
        assertThat(migration).contains("create table learning_event");
        assertThat(migration).contains("create table mastery_profile");
        assertThat(migration).contains("event_key char(64) not null unique");
        assertThat(migration).contains("reverses_event_id bigint unique");
        assertThat(migration).contains("algorithm_version varchar(64) not null");
        assertThat(migration).contains("unique (user_id, knowledge_point_id, algorithm_version)");
        assertThat(migration).contains("revoke all on knowledge_point, learning_event, mastery_profile from learnflow_agent");
        assertThat(migration).doesNotContain("question text");
        assertThat(migration).doesNotContain("answer_user");
        assertThat(migration).doesNotContain("comment text");
        assertThat(migration).doesNotContain("grant select on learning_event to learnflow_agent");
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = MasteryMigrationContractTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as("migration resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

