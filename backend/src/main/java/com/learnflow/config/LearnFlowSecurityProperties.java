package com.learnflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "learnflow.security")
public class LearnFlowSecurityProperties {

    /**
     * Sprint 1 keeps this disabled while the frontend and API contracts are migrated.
     * A production deployment must not expose the application until Sprint 2 enables it.
     */
    private boolean enforceAuthentication = true;

    public boolean isEnforceAuthentication() {
        return enforceAuthentication;
    }

    public void setEnforceAuthentication(boolean enforceAuthentication) {
        this.enforceAuthentication = enforceAuthentication;
    }
}
