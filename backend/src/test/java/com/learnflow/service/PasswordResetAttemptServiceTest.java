package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetAttemptServiceTest {

    @Test
    void blocksAfterConfiguredRequestCount() {
        LearnFlowAuthProperties properties = new LearnFlowAuthProperties();
        properties.setPasswordResetMaxRequests(2);
        properties.setPasswordResetRequestWindow(Duration.ofMinutes(30));
        PasswordResetAttemptService service = new PasswordResetAttemptService(properties,
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC));
        String key = service.key(" Alice ", "192.0.2.10");

        service.recordRequest(key);
        service.assertAllowed(key);
        service.recordRequest(key);

        assertThat(key).isEqualTo("alice|192.0.2.10");
        assertThatThrownBy(() -> service.assertAllowed(key))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
}
