package com.learnflow.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivacyProductionConfigurationTest {
    @Test
    void productionRejectsDevelopmentPrivacyPepper() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "production", "learnflow_backend", "backend-strong-secret-2026",
                "learnflow_migrator", "migration-strong-secret-2026", "production",
                true, true, "https://learnflow.example.com", true,
                "commit-0123456789abcdef", false, false, "dev-only-change-this-privacy-pepper"
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("privacy subject hash");
    }

    @Test
    void productionRejectsDocumentedPrivacyPepperPlaceholder() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "production", "learnflow_backend", "backend-strong-secret-2026",
                "learnflow_migrator", "migration-strong-secret-2026", "production",
                true, true, "https://learnflow.example.com", true,
                "commit-0123456789abcdef", false, false,
                "replace_with_independent_32_byte_random_secret"
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("privacy subject hash");
    }
}
