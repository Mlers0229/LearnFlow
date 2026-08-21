package com.learnflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "learnflow.auth")
public class LearnFlowAuthProperties {

    private String environment = "development";
    private String issuer = "learnflow-backend";
    private String audience = "learnflow-web";
    private String jwtSecret = "dev-only-change-this-jwt-secret-32-bytes";
    private String jwtKeyId = "current";
    private String previousJwtSecret;
    private String previousJwtKeyId = "previous";
    private Duration accessTokenTtl = Duration.ofMinutes(10);
    private Duration refreshTokenTtl = Duration.ofDays(14);
    private String refreshCookieName = "learnflow_refresh";
    private boolean secureCookie = true;
    private String sameSite = "Lax";
    private int loginMaxFailures = 5;
    private Duration loginWindow = Duration.ofMinutes(15);
    private boolean passwordResetEnabled = false;
    private Duration passwordResetTtl = Duration.ofMinutes(20);
    private String passwordResetBaseUrl = "http://localhost:5173/reset-password";
    private String passwordResetMailFrom = "no-reply@learnflow.local";
    private int passwordResetMaxRequests = 3;
    private Duration passwordResetRequestWindow = Duration.ofMinutes(30);

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public String getJwtKeyId() { return jwtKeyId; }
    public void setJwtKeyId(String jwtKeyId) { this.jwtKeyId = jwtKeyId; }
    public String getPreviousJwtSecret() { return previousJwtSecret; }
    public void setPreviousJwtSecret(String previousJwtSecret) { this.previousJwtSecret = previousJwtSecret; }
    public String getPreviousJwtKeyId() { return previousJwtKeyId; }
    public void setPreviousJwtKeyId(String previousJwtKeyId) { this.previousJwtKeyId = previousJwtKeyId; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String refreshCookieName) { this.refreshCookieName = refreshCookieName; }
    public boolean isSecureCookie() { return secureCookie; }
    public void setSecureCookie(boolean secureCookie) { this.secureCookie = secureCookie; }
    public String getSameSite() { return sameSite; }
    public void setSameSite(String sameSite) { this.sameSite = sameSite; }
    public int getLoginMaxFailures() { return loginMaxFailures; }
    public void setLoginMaxFailures(int loginMaxFailures) { this.loginMaxFailures = loginMaxFailures; }
    public Duration getLoginWindow() { return loginWindow; }
    public void setLoginWindow(Duration loginWindow) { this.loginWindow = loginWindow; }
    public boolean isPasswordResetEnabled() { return passwordResetEnabled; }
    public void setPasswordResetEnabled(boolean passwordResetEnabled) { this.passwordResetEnabled = passwordResetEnabled; }
    public Duration getPasswordResetTtl() { return passwordResetTtl; }
    public void setPasswordResetTtl(Duration passwordResetTtl) { this.passwordResetTtl = passwordResetTtl; }
    public String getPasswordResetBaseUrl() { return passwordResetBaseUrl; }
    public void setPasswordResetBaseUrl(String passwordResetBaseUrl) { this.passwordResetBaseUrl = passwordResetBaseUrl; }
    public String getPasswordResetMailFrom() { return passwordResetMailFrom; }
    public void setPasswordResetMailFrom(String passwordResetMailFrom) { this.passwordResetMailFrom = passwordResetMailFrom; }
    public int getPasswordResetMaxRequests() { return passwordResetMaxRequests; }
    public void setPasswordResetMaxRequests(int passwordResetMaxRequests) { this.passwordResetMaxRequests = passwordResetMaxRequests; }
    public Duration getPasswordResetRequestWindow() { return passwordResetRequestWindow; }
    public void setPasswordResetRequestWindow(Duration passwordResetRequestWindow) { this.passwordResetRequestWindow = passwordResetRequestWindow; }
}
