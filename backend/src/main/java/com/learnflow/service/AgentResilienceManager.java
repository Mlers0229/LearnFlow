package com.learnflow.service;

import com.learnflow.config.LearnFlowAiAgentProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class AgentResilienceManager {

    private static final Logger log = LoggerFactory.getLogger(AgentResilienceManager.class);

    private final Map<AgentOperation, Bulkhead> bulkheads = new EnumMap<>(AgentOperation.class);
    private final Map<AgentOperation, CircuitBreaker> circuitBreakers = new EnumMap<>(AgentOperation.class);
    private final Map<AgentOperation, Retry> retries = new EnumMap<>(AgentOperation.class);

    @Autowired
    public AgentResilienceManager(LearnFlowAiAgentProperties properties, MeterRegistry meterRegistry) {
        LearnFlowAiAgentProperties.Resilience resilience = properties.getResilience();
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(resilience.getFailureRateThreshold())
                .minimumNumberOfCalls(resilience.getMinimumCalls())
                .slidingWindowSize(resilience.getMinimumCalls())
                .permittedNumberOfCallsInHalfOpenState(resilience.getHalfOpenCalls())
                .waitDurationInOpenState(resilience.getOpenStateWait())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(AgentResilienceManager::recordsCircuitFailure)
                .build();
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(resilience.getRetryMaxAttempts())
                .waitDuration(resilience.getRetryWait())
                .retryOnException(AgentResilienceManager::isRetryable)
                .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        for (AgentOperation operation : AgentOperation.values()) {
            String name = "agent-" + operation.name().toLowerCase();
            BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                    .maxConcurrentCalls(concurrencyFor(properties, operation))
                    .maxWaitDuration(resilience.getBulkheadWait())
                    .build();
            Bulkhead bulkhead = BulkheadRegistry.of(bulkheadConfig).bulkhead(name);
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
            Retry retry = retryRegistry.retry(name + "-safe-read");
            bulkhead.getEventPublisher().onCallRejected(event -> {
                incrementResilienceMetric(meterRegistry, operation, "bulkhead_rejected");
                log.warn(
                        "Agent bulkhead rejected operation={} availableConcurrentCalls={}",
                        operation,
                        bulkhead.getMetrics().getAvailableConcurrentCalls()
                );
            });
            circuitBreaker.getEventPublisher().onStateTransition(event -> {
                incrementResilienceMetric(
                        meterRegistry,
                        operation,
                        "circuit_" + event.getStateTransition().getToState().name().toLowerCase()
                );
                log.warn(
                        "Agent circuit state changed operation={} transition={}",
                        operation,
                        event.getStateTransition()
                );
            });
            retry.getEventPublisher().onRetry(event -> {
                incrementResilienceMetric(meterRegistry, operation, "retry");
                log.warn(
                        "Agent safe read retry operation={} attempt={} lastErrorType={}",
                        operation,
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() == null
                                ? "unknown"
                                : event.getLastThrowable().getClass().getSimpleName()
                );
            });
            bulkheads.put(operation, bulkhead);
            circuitBreakers.put(operation, circuitBreaker);
            retries.put(operation, retry);
        }
    }

    AgentResilienceManager(LearnFlowAiAgentProperties properties) {
        this(properties, new SimpleMeterRegistry());
    }

    public <T> T execute(AgentOperation operation, boolean safeToRetry, Supplier<T> action) {
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(circuitBreakers.get(operation), action);
        if (safeToRetry) {
            decorated = Retry.decorateSupplier(retries.get(operation), decorated);
        }
        decorated = Bulkhead.decorateSupplier(bulkheads.get(operation), decorated);
        try {
            return decorated.get();
        } catch (BulkheadFullException exception) {
            throw new AgentCallException(
                    operation,
                    AgentCallException.Reason.BULKHEAD_FULL,
                    "Agent concurrency limit reached",
                    exception,
                    null
            );
        } catch (CallNotPermittedException exception) {
            log.warn(
                    "Agent call rejected operation={} reason=circuit_open state={}",
                    operation,
                    circuitBreakers.get(operation).getState()
            );
            throw new AgentCallException(
                    operation,
                    AgentCallException.Reason.CIRCUIT_OPEN,
                    "Agent circuit breaker is open",
                    exception,
                    null
            );
        }
    }

    CircuitBreaker.State circuitState(AgentOperation operation) {
        return circuitBreakers.get(operation).getState();
    }

    private static void incrementResilienceMetric(
            MeterRegistry meterRegistry,
            AgentOperation operation,
            String event
    ) {
        meterRegistry.counter(
                "learnflow.ai.agent.resilience.events",
                Tags.of("operation", operation.name().toLowerCase(), "event", event)
        ).increment();
    }

    private static boolean recordsCircuitFailure(Throwable throwable) {
        if (!(throwable instanceof AgentCallException exception)) {
            return false;
        }
        return switch (exception.getReason()) {
            case CONNECT_TIMEOUT, READ_TIMEOUT, OVERALL_TIMEOUT, IO_ERROR -> true;
            case HTTP_ERROR -> exception.getStatusCode() != null && exception.getStatusCode() >= 500;
            case CANCELLED, BULKHEAD_FULL, CIRCUIT_OPEN, SERIALIZATION_ERROR -> false;
        };
    }

    private static boolean isRetryable(Throwable throwable) {
        if (!(throwable instanceof AgentCallException exception)) {
            return false;
        }
        return switch (exception.getReason()) {
            case CONNECT_TIMEOUT, READ_TIMEOUT, IO_ERROR -> true;
            case HTTP_ERROR -> exception.getStatusCode() != null
                    && (exception.getStatusCode() == 502
                    || exception.getStatusCode() == 503
                    || exception.getStatusCode() == 504);
            case OVERALL_TIMEOUT, CANCELLED, BULKHEAD_FULL, CIRCUIT_OPEN, SERIALIZATION_ERROR -> false;
        };
    }

    private static int concurrencyFor(LearnFlowAiAgentProperties properties, AgentOperation operation) {
        LearnFlowAiAgentProperties.Concurrency concurrency = properties.getResilience().getConcurrency();
        return switch (operation) {
            case ADMIN -> concurrency.getAdmin();
            case RAG -> concurrency.getRag();
            case TUTOR -> concurrency.getTutor();
            case PLAN -> concurrency.getPlan();
            case STREAM -> concurrency.getStream();
        };
    }
}
