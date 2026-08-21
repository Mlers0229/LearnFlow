package com.learnflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "learnflow.ai-agent")
public class LearnFlowAiAgentProperties {

    private String baseUrl = "http://localhost:8000";
    private String internalToken = "dev-only-change-this-agent-token";
    private String environment = "development";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration writeTimeout = Duration.ofSeconds(15);
    private Duration defaultReadTimeout = Duration.ofSeconds(60);
    private Duration keepAlive = Duration.ofMinutes(5);
    private int maxConnections = 30;
    private int maxIdleConnections = 10;
    private final Budgets budgets = new Budgets();
    private final Resilience resilience = new Resilience();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public Duration getDefaultReadTimeout() {
        return defaultReadTimeout;
    }

    public void setDefaultReadTimeout(Duration defaultReadTimeout) {
        this.defaultReadTimeout = defaultReadTimeout;
    }

    public Duration getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(Duration keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    public void setMaxIdleConnections(int maxIdleConnections) {
        this.maxIdleConnections = maxIdleConnections;
    }

    public Budgets getBudgets() {
        return budgets;
    }

    public Resilience getResilience() {
        return resilience;
    }

    public static class Budgets {
        private Duration admin = Duration.ofSeconds(10);
        private Duration rag = Duration.ofSeconds(15);
        private Duration tutor = Duration.ofSeconds(45);
        private Duration plan = Duration.ofSeconds(90);
        private Duration stream = Duration.ofMinutes(5);
        private Duration streamIdle = Duration.ofSeconds(60);

        public Duration getAdmin() {
            return admin;
        }

        public void setAdmin(Duration admin) {
            this.admin = admin;
        }

        public Duration getRag() {
            return rag;
        }

        public void setRag(Duration rag) {
            this.rag = rag;
        }

        public Duration getTutor() {
            return tutor;
        }

        public void setTutor(Duration tutor) {
            this.tutor = tutor;
        }

        public Duration getPlan() {
            return plan;
        }

        public void setPlan(Duration plan) {
            this.plan = plan;
        }

        public Duration getStream() {
            return stream;
        }

        public void setStream(Duration stream) {
            this.stream = stream;
        }

        public Duration getStreamIdle() {
            return streamIdle;
        }

        public void setStreamIdle(Duration streamIdle) {
            this.streamIdle = streamIdle;
        }
    }

    public static class Resilience {
        private int retryMaxAttempts = 2;
        private Duration retryWait = Duration.ofMillis(100);
        private float failureRateThreshold = 50.0f;
        private int minimumCalls = 5;
        private Duration openStateWait = Duration.ofSeconds(20);
        private int halfOpenCalls = 2;
        private Duration bulkheadWait = Duration.ZERO;
        private final Concurrency concurrency = new Concurrency();

        public int getRetryMaxAttempts() {
            return retryMaxAttempts;
        }

        public void setRetryMaxAttempts(int retryMaxAttempts) {
            this.retryMaxAttempts = retryMaxAttempts;
        }

        public Duration getRetryWait() {
            return retryWait;
        }

        public void setRetryWait(Duration retryWait) {
            this.retryWait = retryWait;
        }

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getMinimumCalls() {
            return minimumCalls;
        }

        public void setMinimumCalls(int minimumCalls) {
            this.minimumCalls = minimumCalls;
        }

        public Duration getOpenStateWait() {
            return openStateWait;
        }

        public void setOpenStateWait(Duration openStateWait) {
            this.openStateWait = openStateWait;
        }

        public int getHalfOpenCalls() {
            return halfOpenCalls;
        }

        public void setHalfOpenCalls(int halfOpenCalls) {
            this.halfOpenCalls = halfOpenCalls;
        }

        public Duration getBulkheadWait() {
            return bulkheadWait;
        }

        public void setBulkheadWait(Duration bulkheadWait) {
            this.bulkheadWait = bulkheadWait;
        }

        public Concurrency getConcurrency() {
            return concurrency;
        }
    }

    public static class Concurrency {
        private int admin = 4;
        private int rag = 8;
        private int tutor = 8;
        private int plan = 4;
        private int stream = 6;

        public int getAdmin() {
            return admin;
        }

        public void setAdmin(int admin) {
            this.admin = admin;
        }

        public int getRag() {
            return rag;
        }

        public void setRag(int rag) {
            this.rag = rag;
        }

        public int getTutor() {
            return tutor;
        }

        public void setTutor(int tutor) {
            this.tutor = tutor;
        }

        public int getPlan() {
            return plan;
        }

        public void setPlan(int plan) {
            this.plan = plan;
        }

        public int getStream() {
            return stream;
        }

        public void setStream(int stream) {
            this.stream = stream;
        }
    }
}
