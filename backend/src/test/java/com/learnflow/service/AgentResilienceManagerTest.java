package com.learnflow.service;

import com.learnflow.config.LearnFlowAiAgentProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResilienceManagerTest {

    @Test
    void retriesTransientFailureOnlyWhenCallerMarksOperationSafe() {
        LearnFlowAiAgentProperties properties = propertiesForTest();
        AgentResilienceManager manager = new AgentResilienceManager(properties);
        AtomicInteger safeCalls = new AtomicInteger();

        String result = manager.execute(AgentOperation.ADMIN, true, () -> {
            if (safeCalls.incrementAndGet() == 1) {
                throw transientFailure();
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, safeCalls.get());

        AtomicInteger unsafeCalls = new AtomicInteger();
        assertThrows(AgentCallException.class, () -> manager.execute(AgentOperation.PLAN, false, () -> {
            unsafeCalls.incrementAndGet();
            throw transientFailure();
        }));
        assertEquals(1, unsafeCalls.get());
    }

    @Test
    void rejectsWhenOperationBulkheadIsSaturated() throws Exception {
        LearnFlowAiAgentProperties properties = propertiesForTest();
        properties.getResilience().getConcurrency().setAdmin(1);
        AgentResilienceManager manager = new AgentResilienceManager(properties);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> first = executor.submit(() -> manager.execute(AgentOperation.ADMIN, false, () -> {
                entered.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return "done";
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            AgentCallException rejected = assertThrows(
                    AgentCallException.class,
                    () -> manager.execute(AgentOperation.ADMIN, false, () -> "second")
            );
            assertEquals(AgentCallException.Reason.BULKHEAD_FULL, rejected.getReason());

            release.countDown();
            assertEquals("done", first.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void opensCircuitAndRecoversThroughHalfOpenProbe() throws Exception {
        LearnFlowAiAgentProperties properties = propertiesForTest();
        properties.getResilience().setMinimumCalls(2);
        properties.getResilience().setHalfOpenCalls(1);
        properties.getResilience().setOpenStateWait(Duration.ofMillis(30));
        AgentResilienceManager manager = new AgentResilienceManager(properties);

        assertThrows(
                AgentCallException.class,
                () -> manager.execute(AgentOperation.RAG, false, () -> {
                    throw transientFailure();
                })
        );
        assertThrows(
                AgentCallException.class,
                () -> manager.execute(AgentOperation.RAG, false, () -> {
                    throw transientFailure();
                })
        );
        assertEquals(CircuitBreaker.State.OPEN, manager.circuitState(AgentOperation.RAG));

        AgentCallException rejected = assertThrows(
                AgentCallException.class,
                () -> manager.execute(AgentOperation.RAG, false, () -> "blocked")
        );
        assertEquals(AgentCallException.Reason.CIRCUIT_OPEN, rejected.getReason());

        Thread.sleep(60);
        assertEquals("recovered", manager.execute(AgentOperation.RAG, false, () -> "recovered"));
        assertEquals(CircuitBreaker.State.CLOSED, manager.circuitState(AgentOperation.RAG));
    }

    private static LearnFlowAiAgentProperties propertiesForTest() {
        LearnFlowAiAgentProperties properties = new LearnFlowAiAgentProperties();
        properties.getResilience().setRetryWait(Duration.ofMillis(1));
        return properties;
    }

    private static AgentCallException transientFailure() {
        return new AgentCallException(
                AgentOperation.ADMIN,
                AgentCallException.Reason.IO_ERROR,
                "temporary failure",
                null,
                null
        );
    }
}
