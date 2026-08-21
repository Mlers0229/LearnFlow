package com.learnflow.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(LearnFlowAuthProperties.class)
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(LearnFlowAuthProperties properties) {
        boolean production = "production".equalsIgnoreCase(properties.getEnvironment());
        if (production && properties.getJwtSecret().startsWith("dev-only-")) {
            throw new IllegalStateException("Production requires an injected LEARNFLOW_JWT_SECRET");
        }
        if (production && !properties.isSecureCookie()) {
            throw new IllegalStateException("Production requires LEARNFLOW_SECURE_COOKIE=true");
        }
        byte[] secret = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("LEARNFLOW_JWT_SECRET must contain at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey, LearnFlowAuthProperties properties) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey)
                .keyID(properties.getJwtKeyId())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey secretKey, LearnFlowAuthProperties properties) {
        JwtDecoder activeDecoder = configuredDecoder(secretKey, properties);
        String previousSecret = properties.getPreviousJwtSecret();
        if (previousSecret == null || previousSecret.isBlank()) {
            return activeDecoder;
        }
        SecretKey previousKey = secretKey(previousSecret, "LEARNFLOW_JWT_PREVIOUS_SECRET");
        JwtDecoder previousDecoder = configuredDecoder(previousKey, properties);
        return token -> {
            try {
                return activeDecoder.decode(token);
            } catch (JwtException activeFailure) {
                return previousDecoder.decode(token);
            }
        };
    }

    private NimbusJwtDecoder configuredDecoder(SecretKey secretKey, LearnFlowAuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.getAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    private SecretKey secretKey(String value, String name) {
        byte[] secret = value.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(name + " must contain at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
