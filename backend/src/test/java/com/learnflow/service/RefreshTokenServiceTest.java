package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import com.learnflow.entity.RefreshTokenSession;
import com.learnflow.entity.User;
import com.learnflow.repository.RefreshTokenSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RefreshTokenServiceTest {

    @Test
    void storesOnlyHashOfRefreshToken() {
        RefreshTokenSessionRepository repository = mock(RefreshTokenSessionRepository.class);
        LearnFlowAuthProperties properties = new LearnFlowAuthProperties();
        properties.setRefreshTokenTtl(Duration.ofDays(14));
        RefreshTokenService service = new RefreshTokenService(repository, properties);
        User user = new User();
        user.setId(1L);

        RefreshTokenService.IssuedRefreshToken issued = service.create(user);

        ArgumentCaptor<RefreshTokenSession> captor = ArgumentCaptor.forClass(RefreshTokenSession.class);
        verify(repository).save(captor.capture());
        RefreshTokenSession stored = captor.getValue();
        assertThat(issued.value()).isNotBlank();
        assertThat(stored.getTokenHash()).isEqualTo(RefreshTokenService.hash(issued.value()));
        assertThat(stored.getTokenHash()).doesNotContain(issued.value());
        assertThat(stored.getUser()).isSameAs(user);
        assertThat(stored.getFamilyId()).isNotBlank();
    }
}
