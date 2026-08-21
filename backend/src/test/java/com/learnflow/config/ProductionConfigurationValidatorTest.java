package com.learnflow.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {

    @Test
    void productionRejectsSharedOrPlaceholderDatabaseCredentials() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "production",
                "learnflow_user",
                "change_me",
                "learnflow_user",
                "change_me"
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database password");
    }

    @Test
    void productionAcceptsStrongDistinctRuntimeAndMigrationRoles() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "production",
                "learnflow_backend",
                "backend-strong-secret-2026",
                "learnflow_migrator",
                "migration-strong-secret-2026"
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
