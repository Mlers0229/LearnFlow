package com.learnflow.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductionConfigurationValidator {

    private final String environment;
    private final String runtimeUser;
    private final String runtimePassword;
    private final String migrationUser;
    private final String migrationPassword;

    public ProductionConfigurationValidator(
            @Value("${learnflow.auth.environment:development}") String environment,
            @Value("${spring.datasource.username:}") String runtimeUser,
            @Value("${spring.datasource.password:}") String runtimePassword,
            @Value("${spring.flyway.user:${spring.datasource.username:}}") String migrationUser,
            @Value("${spring.flyway.password:${spring.datasource.password:}}") String migrationPassword
    ) {
        this.environment = environment;
        this.runtimeUser = runtimeUser;
        this.runtimePassword = runtimePassword;
        this.migrationUser = migrationUser;
        this.migrationPassword = migrationPassword;
    }

    @PostConstruct
    void validate() {
        if (!"production".equalsIgnoreCase(environment)) {
            return;
        }
        requireStrongPassword(runtimePassword, "Backend database password");
        requireStrongPassword(migrationPassword, "Migration database password");
        if (runtimeUser.isBlank() || migrationUser.isBlank() || runtimeUser.equals(migrationUser)) {
            throw new IllegalStateException("Production requires distinct migration and Backend database roles");
        }
    }

    private void requireStrongPassword(String value, String label) {
        if (value == null || value.length() < 16 || value.startsWith("change_") || "change_me".equals(value)) {
            throw new IllegalStateException(label + " must be injected as a strong Secret");
        }
    }
}
