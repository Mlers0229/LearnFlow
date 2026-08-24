package com.learnflow.service;

import com.learnflow.config.LearnFlowPrivacyProperties;
import com.learnflow.dto.AccountErasureRequest;
import com.learnflow.entity.User;
import com.learnflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyRequestServiceTest {
    @Mock private PrivacyRequestStore store;
    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokens;
    @Mock private ResourceSourceStore sourceStore;

    private LearnFlowPrivacyProperties properties;
    private PrivacyRequestService service;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new LearnFlowPrivacyProperties();
        properties.setSubjectHashPepper("test-privacy-pepper-at-least-32-bytes");
        service = new PrivacyRequestService(store, users, passwordEncoder, refreshTokens, sourceStore, properties);
        user = new User();
        user.setId(42L);
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setRole("student");
        user.setStatus("ACTIVE");
        when(users.findById(42L)).thenReturn(Optional.of(user));
    }

    @Test
    void exportCreatesPrivacyMinimizedIdempotentRequest() {
        UUID id = UUID.randomUUID();
        when(store.createOrGet(any(), eq(42L), anyString(), eq("EXPORT"), anyString()))
                .thenReturn(record(id, "EXPORT", "PENDING"));

        var response = service.requestExport(42L, "export:request-1234");

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.type()).isEqualTo("EXPORT");
        assertThat(response.downloadReady()).isFalse();
        verify(store).createOrGet(any(), eq(42L), anyString(), eq("EXPORT"), anyString());
    }

    @Test
    void erasureDisablesAccountAndRevokesRefreshSessions() {
        UUID id = UUID.randomUUID();
        AccountErasureRequest input = new AccountErasureRequest();
        input.setPassword("correct-password");
        input.setConfirmation("DELETE alice");
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(store.createOrGetActiveErasure(any(), eq(42L), anyString(), anyString()))
                .thenReturn(record(id, "ERASURE", "PENDING"));

        var response = service.requestErasure(42L, "erasure:request-1234", input);

        assertThat(response.id()).isEqualTo(id);
        assertThat(user.getStatus()).isEqualTo("DISABLED");
        verify(store).cancelPendingExportsForErasure(42L);
        verify(users).save(user);
        verify(refreshTokens).revokeAllForUser(42L);
    }

    @Test
    void adminCannotUseSelfServiceErasure() {
        user.setRole("admin");
        AccountErasureRequest input = new AccountErasureRequest();
        input.setPassword("correct-password");
        input.setConfirmation("DELETE alice");

        assertThatThrownBy(() -> service.requestErasure(42L, "erasure:request-1234", input))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("管理员账户");
        verify(store, never()).createOrGetActiveErasure(any(), anyLong(), anyString(), anyString());
    }

    private PrivacyRequestStore.PrivacyRequestRecord record(UUID id, String type, String status) {
        return new PrivacyRequestStore.PrivacyRequestRecord(
                id, 42L, type, status, 0, 5, null, null, null,
                null, null, null, OffsetDateTime.now(ZoneOffset.UTC), null);
    }
}
