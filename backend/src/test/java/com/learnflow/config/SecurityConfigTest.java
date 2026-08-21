package com.learnflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void passwordEncoderUsesNonPlaintextHash() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String encoded = encoder.encode("correct-horse-battery-staple");

        assertThat(encoded).isNotEqualTo("correct-horse-battery-staple");
        assertThat(encoder.matches("correct-horse-battery-staple", encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void corsConfigurationUsesExplicitOriginsAndCredentials() {
        LearnFlowCorsProperties properties = new LearnFlowCorsProperties();
        properties.setAllowedOrigins(List.of("https://learnflow.example.com"));
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(properties);

        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("GET", "/api/plans")
        );

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://learnflow.example.com");
        assertThat(configuration.getAllowedOrigins()).doesNotContain("*");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    void authenticationEnforcementIsFailClosedByDefault() {
        LearnFlowSecurityProperties properties = new LearnFlowSecurityProperties();

        assertThat(properties.isEnforceAuthentication()).isTrue();

        properties.setEnforceAuthentication(false);
        assertThat(properties.isEnforceAuthentication()).isFalse();
    }
}
