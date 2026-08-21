package com.learnflow.service;

import com.learnflow.config.LearnFlowAuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final LearnFlowAuthProperties properties;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(LearnFlowAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptService(LearnFlowAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String key(String username, String remoteAddress) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return normalizedUsername + "|" + (remoteAddress == null ? "unknown" : remoteAddress);
    }

    public void assertAllowed(String key) {
        AttemptWindow window = attempts.get(key);
        Instant now = clock.instant();
        if (window != null && window.blockedUntil().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过多，请稍后再试");
        }
        if (window != null && !window.windowEndsAt().isAfter(now)) {
            attempts.remove(key, window);
        }
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (ignored, existing) -> {
            AttemptWindow current = existing;
            if (current == null || !current.windowEndsAt().isAfter(now)) {
                current = new AttemptWindow(0, now.plus(properties.getLoginWindow()), Instant.EPOCH);
            }
            int failures = current.failures() + 1;
            Instant blockedUntil = failures >= properties.getLoginMaxFailures()
                    ? now.plus(properties.getLoginWindow())
                    : Instant.EPOCH;
            return new AttemptWindow(failures, current.windowEndsAt(), blockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private record AttemptWindow(int failures, Instant windowEndsAt, Instant blockedUntil) {}
}
