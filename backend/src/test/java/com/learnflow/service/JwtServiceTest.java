package com.learnflow.service;

import com.learnflow.config.JwtConfig;
import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @Test
    void issuedTokenContainsTrustedIdentityAndPassesValidation() {
        LearnFlowAuthProperties properties = new LearnFlowAuthProperties();
        properties.setIssuer("learnflow-test");
        properties.setAudience("learnflow-test-web");
        properties.setJwtSecret("0123456789abcdef0123456789abcdef");
        properties.setAccessTokenTtl(Duration.ofMinutes(10));

        JwtConfig config = new JwtConfig();
        SecretKey key = config.jwtSecretKey(properties);
        JwtEncoder encoder = config.jwtEncoder(key, properties);
        JwtDecoder decoder = config.jwtDecoder(key, properties);
        JwtService service = new JwtService(encoder, properties);

        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setRole("admin");

        JwtService.IssuedAccessToken issued = service.issue(user);
        Jwt decoded = decoder.decode(issued.value());

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("username")).isEqualTo("alice");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(decoded.getAudience()).containsExactly("learnflow-test-web");
        assertThat(issued.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void productionRejectsDevelopmentSigningSecret() {
        LearnFlowAuthProperties properties = new LearnFlowAuthProperties();
        properties.setEnvironment("production");
        properties.setJwtSecret("dev-only-change-this-jwt-secret-32-bytes");

        assertThatThrownBy(() -> new JwtConfig().jwtSecretKey(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEARNFLOW_JWT_SECRET");
    }

    @Test
    void rotatedDecoderAcceptsPreviousSecretWhileEncoderUsesCurrentSecret() {
        LearnFlowAuthProperties oldProperties = new LearnFlowAuthProperties();
        oldProperties.setIssuer("learnflow-test");
        oldProperties.setAudience("learnflow-web");
        oldProperties.setJwtSecret("old-secret-0123456789abcdef0123456789abcdef");
        oldProperties.setJwtKeyId("old-key");

        JwtConfig config = new JwtConfig();
        SecretKey oldKey = config.jwtSecretKey(oldProperties);
        JwtService oldIssuer = new JwtService(config.jwtEncoder(oldKey, oldProperties), oldProperties);
        User user = new User();
        user.setId(7L);
        user.setUsername("rotating-user");
        user.setRole("student");
        String oldToken = oldIssuer.issue(user).value();

        LearnFlowAuthProperties rotated = new LearnFlowAuthProperties();
        rotated.setIssuer("learnflow-test");
        rotated.setAudience("learnflow-web");
        rotated.setJwtSecret("new-secret-0123456789abcdef0123456789abcdef");
        rotated.setJwtKeyId("new-key");
        rotated.setPreviousJwtSecret(oldProperties.getJwtSecret());
        SecretKey newKey = config.jwtSecretKey(rotated);

        assertThat(config.jwtDecoder(newKey, rotated).decode(oldToken).getSubject()).isEqualTo("7");
        assertThat(new JwtService(config.jwtEncoder(newKey, rotated), rotated).issue(user).value())
                .isNotEqualTo(oldToken);
    }
}
