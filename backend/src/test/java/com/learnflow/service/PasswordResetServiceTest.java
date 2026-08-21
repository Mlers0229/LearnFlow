package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.entity.PasswordResetToken;
import com.learnflow.entity.User;
import com.learnflow.repository.PasswordResetTokenRepository;
import com.learnflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    @Test
    void requestStoresOnlyHashAndDeliversRawToken() {
        UserRepository users = mock(UserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        PasswordResetDelivery delivery = mock(PasswordResetDelivery.class);
        LearnFlowAuthProperties properties = properties();
        User user = user();
        when(users.findByUsernameAndEmailIgnoreCase("alice", "alice@example.com"))
                .thenReturn(Optional.of(user));
        when(tokens.findAllByUser_IdAndUsedAtIsNull(42L)).thenReturn(List.of());

        service(users, tokens, encoder, refreshTokens, delivery, properties)
                .request("alice", "alice@example.com");

        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(tokens).save(stored.capture());
        verify(delivery).send(eq(user), delivered.capture());
        assertThat(stored.getValue().getTokenHash()).isEqualTo(PasswordResetService.hash(delivered.getValue()));
        assertThat(stored.getValue().getTokenHash()).doesNotContain(delivered.getValue());
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(20)));
    }

    @Test
    void confirmConsumesTokenChangesPasswordAndRevokesSessions() {
        UserRepository users = mock(UserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        PasswordResetDelivery delivery = mock(PasswordResetDelivery.class);
        User user = user();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(PasswordResetService.hash("raw-token"));
        token.setCreatedAt(NOW.minusSeconds(30));
        token.setExpiresAt(NOW.plusSeconds(300));
        when(tokens.findForUpdateByTokenHash(PasswordResetService.hash("raw-token")))
                .thenReturn(Optional.of(token));
        when(encoder.encode("a-new-strong-password")).thenReturn("new-hash");

        service(users, tokens, encoder, refreshTokens, delivery, properties())
                .confirm("raw-token", "a-new-strong-password");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(refreshTokens).revokeAllForUser(42L);
    }

    private PasswordResetService service(UserRepository users,
                                         PasswordResetTokenRepository tokens,
                                         PasswordEncoder encoder,
                                         RefreshTokenService refreshTokens,
                                         PasswordResetDelivery delivery,
                                         LearnFlowAuthProperties properties) {
        return new PasswordResetService(users, tokens, encoder, refreshTokens, delivery, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LearnFlowAuthProperties properties() {
        LearnFlowAuthProperties properties = new LearnFlowAuthProperties();
        properties.setPasswordResetEnabled(true);
        properties.setPasswordResetTtl(Duration.ofMinutes(20));
        return properties;
    }

    private User user() {
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setStatus("ACTIVE");
        user.setPasswordHash("old-hash");
        return user;
    }
}
