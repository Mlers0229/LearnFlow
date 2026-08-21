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

class LoginAttemptServiceTest {

    @Test
    void blocksUsernameAndAddressAfterConfiguredFailures() {
        LearnFlowAuthProperties properties = new LearnFlowAuthProperties();
        properties.setLoginMaxFailures(3);
        properties.setLoginWindow(Duration.ofMinutes(15));
        LoginAttemptService service = new LoginAttemptService(
                properties,
                Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC)
        );
        String key = service.key("Alice", "127.0.0.1");

        service.recordFailure(key);
        service.recordFailure(key);
        service.assertAllowed(key);
        service.recordFailure(key);

        assertThat(key).isEqualTo("alice|127.0.0.1");
        assertThatThrownBy(() -> service.assertAllowed(key))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void successfulLoginClearsFailureWindow() {
        LearnFlowAuthProperties properties = new LearnFlowAuthProperties();
        properties.setLoginMaxFailures(1);
        LoginAttemptService service = new LoginAttemptService(properties);
        String key = service.key("alice", "127.0.0.1");
        service.recordFailure(key);
        service.recordSuccess(key);

        service.assertAllowed(key);
    }
}
