package com.learnflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "learnflow.audit")
public class LearnFlowAuditProperties {

    private Duration retention = Duration.ofDays(365);
    private int maxDetailLength = 2048;

    public Duration getRetention() { return retention; }
    public void setRetention(Duration retention) { this.retention = retention; }
    public int getMaxDetailLength() { return maxDetailLength; }
    public void setMaxDetailLength(int maxDetailLength) { this.maxDetailLength = maxDetailLength; }
}
