package com.learnflow.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ProductionConfigurationValidator {

    private final String environment;
    private final String runtimeUser;
    private final String runtimePassword;
    private final String migrationUser;
    private final String migrationPassword;
    private final String activeProfiles;
    private final boolean secureCookie;
    private final boolean authenticationEnforced;
    private final String corsAllowedOrigins;
    private final boolean telemetryEnabled;
    private final String releaseVersion;
    private final boolean baselineOnMigrate;
    private final boolean openApiEnabled;

    public ProductionConfigurationValidator(
            @Value("${learnflow.auth.environment:development}") String environment,
            @Value("${spring.datasource.username:}") String runtimeUser,
            @Value("${spring.datasource.password:}") String runtimePassword,
            @Value("${spring.flyway.user:${spring.datasource.username:}}") String migrationUser,
            @Value("${spring.flyway.password:${spring.datasource.password:}}") String migrationPassword,
            @Value("${spring.profiles.active:}") String activeProfiles,
            @Value("${learnflow.auth.secure-cookie:true}") boolean secureCookie,
            @Value("${learnflow.security.enforce-authentication:true}") boolean authenticationEnforced,
            @Value("${learnflow.cors.allowed-origins:}") String corsAllowedOrigins,
            @Value("${management.tracing.enabled:false}") boolean telemetryEnabled,
            @Value("${LEARNFLOW_RELEASE_VERSION:development}") String releaseVersion,
            @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate,
            @Value("${springdoc.api-docs.enabled:false}") boolean openApiEnabled
    ) {
        this.environment = environment;
        this.runtimeUser = runtimeUser;
        this.runtimePassword = runtimePassword;
        this.migrationUser = migrationUser;
        this.migrationPassword = migrationPassword;
        this.activeProfiles = activeProfiles;
        this.secureCookie = secureCookie;
        this.authenticationEnforced = authenticationEnforced;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.telemetryEnabled = telemetryEnabled;
        this.releaseVersion = releaseVersion;
        this.baselineOnMigrate = baselineOnMigrate;
        this.openApiEnabled = openApiEnabled;
    }

    @PostConstruct
    void validate() {
        if (!"production".equalsIgnoreCase(environment)) {
            return;
        }
        if (Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .noneMatch("production"::equalsIgnoreCase)) {
            throw new IllegalStateException("Production requires the production Spring profile");
        }
        requireStrongPassword(runtimePassword, "Backend database password");
        requireStrongPassword(migrationPassword, "Migration database password");
        if (runtimeUser.isBlank() || migrationUser.isBlank() || runtimeUser.equals(migrationUser)) {
            throw new IllegalStateException("Production requires distinct migration and Backend database roles");
        }
        if (!secureCookie) {
            throw new IllegalStateException("Production requires Secure authentication cookies");
        }
        if (!authenticationEnforced) {
            throw new IllegalStateException("Production cannot disable server-side authentication");
        }
        validateCorsOrigins();
        if (!telemetryEnabled) {
            throw new IllegalStateException("Production requires tracing and metrics export");
        }
        if (releaseVersion == null || releaseVersion.isBlank()
                || "development".equalsIgnoreCase(releaseVersion)
                || "latest".equalsIgnoreCase(releaseVersion)) {
            throw new IllegalStateException("Production requires an immutable release version");
        }
        if (baselineOnMigrate) {
            throw new IllegalStateException("Production cannot enable Flyway baseline-on-migrate");
        }
        if (openApiEnabled) {
            throw new IllegalStateException("Production cannot expose OpenAPI documentation");
        }
    }

    private void validateCorsOrigins() {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            throw new IllegalStateException("Production requires explicit HTTPS CORS origins");
        }
        for (String value : corsAllowedOrigins.split(",")) {
            String origin = value.trim().toLowerCase();
            if (!origin.startsWith("https://") || origin.contains("localhost") || origin.contains("*")) {
                throw new IllegalStateException("Production CORS origins must be explicit HTTPS origins");
            }
        }
    }

    private void requireStrongPassword(String value, String label) {
        if (value == null || value.length() < 16 || value.startsWith("change_") || "change_me".equals(value)) {
            throw new IllegalStateException(label + " must be injected as a strong Secret");
        }
    }
}
