package com.learnflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
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
                "change_me",
                "production",
                true,
                true,
                "https://learnflow.example.com",
                true,
                "commit-0123456789abcdef",
                false,
                false,
                "unit-privacy-pepper-32-random-characters"
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
                "migration-strong-secret-2026",
                "production",
                true,
                true,
                "https://learnflow.example.com",
                true,
                "commit-0123456789abcdef",
                false,
                false,
                "unit-privacy-pepper-32-random-characters"
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void productionRejectsDevelopmentRuntimeContract() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "production",
                "learnflow_backend",
                "backend-strong-secret-2026",
                "learnflow_migrator",
                "migration-strong-secret-2026",
                "development",
                false,
                false,
                "http://localhost:5173,*",
                false,
                "development",
                true,
                true,
                "unit-privacy-pepper-32-random-characters"
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production Spring profile");
    }

    @Test
    void productionRejectsUnobservableOrMutableRelease() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "production",
                "learnflow_backend",
                "backend-strong-secret-2026",
                "learnflow_migrator",
                "migration-strong-secret-2026",
                "production",
                true,
                true,
                "https://learnflow.example.com",
                false,
                "latest",
                false,
                false,
                "unit-privacy-pepper-32-random-characters"
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tracing and metrics");
    }

    @Test
    void springContextCanInstantiateProductionValidator() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionConfigurationValidator.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductionConfigurationValidator.class);
                });
    }
}
