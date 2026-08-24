package com.learnflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "learnflow.privacy")
public class LearnFlowPrivacyProperties {
    private boolean enabled = true;
    private int maxAttempts = 5;
    private Duration leaseDuration = Duration.ofMinutes(3);
    private Duration retryBaseDelay = Duration.ofSeconds(10);
    private Duration exportTtl = Duration.ofHours(24);
    private Duration requestRetention = Duration.ofDays(365);
    private long maxExportBytes = 10L * 1024L * 1024L;
    private String subjectHashPepper = "dev-only-change-this-privacy-pepper";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getRetryBaseDelay() { return retryBaseDelay; }
    public void setRetryBaseDelay(Duration retryBaseDelay) { this.retryBaseDelay = retryBaseDelay; }
    public Duration getExportTtl() { return exportTtl; }
    public void setExportTtl(Duration exportTtl) { this.exportTtl = exportTtl; }
    public Duration getRequestRetention() { return requestRetention; }
    public void setRequestRetention(Duration requestRetention) { this.requestRetention = requestRetention; }
    public long getMaxExportBytes() { return maxExportBytes; }
    public void setMaxExportBytes(long maxExportBytes) { this.maxExportBytes = maxExportBytes; }
    public String getSubjectHashPepper() { return subjectHashPepper; }
    public void setSubjectHashPepper(String subjectHashPepper) { this.subjectHashPepper = subjectHashPepper; }
}
