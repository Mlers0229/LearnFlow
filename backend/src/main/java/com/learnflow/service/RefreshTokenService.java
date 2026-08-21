package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.entity.RefreshTokenSession;
import com.learnflow.entity.User;
import com.learnflow.repository.RefreshTokenSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenSessionRepository repository;
    private final LearnFlowAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    @Autowired
    public RefreshTokenService(RefreshTokenSessionRepository repository, LearnFlowAuthProperties properties) {
        this(repository, properties, Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenSessionRepository repository, LearnFlowAuthProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken create(User user) {
        return createInFamily(user, UUID.randomUUID().toString());
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {
        String tokenHash = hash(rawToken);
        RefreshTokenSession current = repository.findForUpdateByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        Instant now = clock.instant();
        if (current.getRevokedAt() != null) {
            revokeFamily(current.getFamilyId(), now);
            throw invalidToken();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            current.setRevokedAt(now);
            repository.save(current);
            throw invalidToken();
        }
        User user = current.getUser();
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            revokeFamily(current.getFamilyId(), now);
            throw invalidToken();
        }

        IssuedRefreshToken replacement = createInFamily(user, current.getFamilyId());
        current.setLastUsedAt(now);
        current.setRevokedAt(now);
        current.setReplacedByTokenHash(hash(replacement.value()));
        repository.save(current);
        return new RotatedRefreshToken(user, replacement);
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findForUpdateByTokenHash(hash(rawToken)).ifPresent(token ->
                revokeFamily(token.getFamilyId(), clock.instant())
        );
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        Instant revokedAt = clock.instant();
        for (RefreshTokenSession token : repository.findAllByUser_Id(userId)) {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(revokedAt);
            }
        }
    }

    private IssuedRefreshToken createInFamily(User user, String familyId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant createdAt = clock.instant();

        RefreshTokenSession session = new RefreshTokenSession();
        session.setUser(user);
        session.setTokenHash(hash(rawToken));
        session.setFamilyId(familyId);
        session.setCreatedAt(createdAt);
        session.setExpiresAt(createdAt.plus(properties.getRefreshTokenTtl()));
        repository.save(session);
        return new IssuedRefreshToken(rawToken, properties.getRefreshTokenTtl().toSeconds());
    }

    private void revokeFamily(String familyId, Instant revokedAt) {
        for (RefreshTokenSession token : repository.findAllByFamilyId(familyId)) {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(revokedAt);
            }
        }
    }

    static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "刷新凭证无效或已过期");
    }

    public record IssuedRefreshToken(String value, long maxAgeSeconds) {}
    public record RotatedRefreshToken(User user, IssuedRefreshToken refreshToken) {}
}
