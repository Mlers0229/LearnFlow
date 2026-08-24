package com.learnflow.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyLifecycleMigrationContractTest {
    @Test
    void v18DefinesDurablePrivacyStateAndAgentDenyBoundary() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V18__add_privacy_request_lifecycle.sql"));

        assertThat(migration)
                .contains("CREATE TABLE privacy_request")
                .contains("ON DELETE SET NULL")
                .contains("idx_privacy_request_claim")
                .contains("uq_privacy_request_active_erasure")
                .contains("artifact_expires_at")
                .contains("REVOKE ALL ON privacy_request FROM learnflow_agent")
                .doesNotContain("password_hash")
                .doesNotContain("email VARCHAR");
    }
}
