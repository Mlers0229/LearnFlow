package com.learnflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "learnflow.tasks")
@Validated
public class LearnFlowTaskProperties {

    private boolean enabled = true;
    @Min(1)
    @Max(16)
    private int concurrency = 2;
    @Min(1)
    @Max(10)
    private int maxAttempts = 3;
    private Duration leaseDuration = Duration.ofMinutes(3);
    private Duration taskTimeout = Duration.ofMinutes(10);
    private Duration retryBaseDelay = Duration.ofSeconds(5);
    private Duration failedPayloadRetention = Duration.ofDays(7);
    @Min(1)
    @Max(200)
    private int failedPageSize = 50;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getTaskTimeout() { return taskTimeout; }
    public void setTaskTimeout(Duration taskTimeout) { this.taskTimeout = taskTimeout; }
    public Duration getRetryBaseDelay() { return retryBaseDelay; }
    public void setRetryBaseDelay(Duration retryBaseDelay) { this.retryBaseDelay = retryBaseDelay; }
    public Duration getFailedPayloadRetention() { return failedPayloadRetention; }
    public void setFailedPayloadRetention(Duration failedPayloadRetention) { this.failedPayloadRetention = failedPayloadRetention; }
    public int getFailedPageSize() { return failedPageSize; }
    public void setFailedPageSize(int failedPageSize) { this.failedPageSize = failedPageSize; }

    @AssertTrue(message = "task durations must be positive and the lease must be shorter than the task timeout")
    public boolean isTimingValid() {
        return isPositive(leaseDuration)
                && isPositive(taskTimeout)
                && isPositive(retryBaseDelay)
                && isPositive(failedPayloadRetention)
                && leaseDuration.compareTo(taskTimeout) < 0;
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
