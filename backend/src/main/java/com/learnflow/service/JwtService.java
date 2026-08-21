package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final LearnFlowAuthProperties properties;
    private final Clock clock;

    @Autowired
    public JwtService(JwtEncoder jwtEncoder, LearnFlowAuthProperties properties) {
        this(jwtEncoder, properties, Clock.systemUTC());
    }

    JwtService(JwtEncoder jwtEncoder, LearnFlowAuthProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience()))
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("username", user.getUsername())
                .claim("roles", List.of(user.getRole().toUpperCase()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .keyId(properties.getJwtKeyId())
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, properties.getAccessTokenTtl().toSeconds());
    }

    public record IssuedAccessToken(String value, long expiresInSeconds) {}
}
