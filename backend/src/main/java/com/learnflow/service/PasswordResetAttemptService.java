package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetAttemptService {

    private final ConcurrentHashMap<String, RequestWindow> requests = new ConcurrentHashMap<>();
    private final LearnFlowAuthProperties properties;
    private final Clock clock;

    @Autowired
    public PasswordResetAttemptService(LearnFlowAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PasswordResetAttemptService(LearnFlowAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String key(String username, String remoteAddress) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalized + "|" + (remoteAddress == null ? "unknown" : remoteAddress);
    }

    public void assertAllowed(String key) {
        RequestWindow window = requests.get(key);
        Instant now = clock.instant();
        if (window != null && window.windowEndsAt().isAfter(now)
                && window.requests() >= properties.getPasswordResetMaxRequests()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "密码重置请求过多，请稍后再试");
        }
        if (window != null && !window.windowEndsAt().isAfter(now)) {
            requests.remove(key, window);
        }
    }

    public void recordRequest(String key) {
        Instant now = clock.instant();
        requests.compute(key, (ignored, existing) -> {
            RequestWindow current = existing;
            if (current == null || !current.windowEndsAt().isAfter(now)) {
                current = new RequestWindow(0, now.plus(properties.getPasswordResetRequestWindow()));
            }
            return new RequestWindow(current.requests() + 1, current.windowEndsAt());
        });
    }

    private record RequestWindow(int requests, Instant windowEndsAt) {}
}
