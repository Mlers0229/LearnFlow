package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.entity.PasswordResetToken;
import com.learnflow.entity.User;
import com.learnflow.repository.PasswordResetTokenRepository;
import com.learnflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetDelivery delivery;
    private final LearnFlowAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    @Autowired
    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenService refreshTokenService,
                                PasswordResetDelivery delivery,
                                LearnFlowAuthProperties properties) {
        this(userRepository, tokenRepository, passwordEncoder, refreshTokenService, delivery, properties, Clock.systemUTC());
    }

    PasswordResetService(UserRepository userRepository,
                         PasswordResetTokenRepository tokenRepository,
                         PasswordEncoder passwordEncoder,
                         RefreshTokenService refreshTokenService,
                         PasswordResetDelivery delivery,
                         LearnFlowAuthProperties properties,
                         Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.delivery = delivery;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void request(String username, String email) {
        if (!properties.isPasswordResetEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "密码重置邮件服务未启用");
        }
        tokenRepository.deleteByExpiresAtBefore(clock.instant());
        userRepository.findByUsernameAndEmailIgnoreCase(username.trim(), email.trim()).ifPresent(this::issueAndDeliver);
    }

    @Transactional
    public void confirm(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findForUpdateByTokenHash(hash(rawToken))
                .orElseThrow(this::invalidToken);
        Instant now = clock.instant();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }
        User user = token.getUser();
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw invalidToken();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(now);
        userRepository.save(user);
        tokenRepository.save(token);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private void issueAndDeliver(User user) {
        if (user.getEmail() == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        for (PasswordResetToken existing : tokenRepository.findAllByUser_IdAndUsedAtIsNull(user.getId())) {
            existing.setUsedAt(now);
        }
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(properties.getPasswordResetTtl()));
        tokenRepository.save(token);
        try {
            delivery.send(user, rawToken);
        } catch (RuntimeException deliveryFailure) {
            token.setUsedAt(now);
            tokenRepository.save(token);
            log.error("Password reset delivery failed; token invalidated. errorType={}",
                    deliveryFailure.getClass().getSimpleName());
        }
    }

    static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码重置凭证无效或已过期");
    }
}
