package com.learnflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.config.LearnFlowAiAgentProperties;
import com.learnflow.config.RequestCorrelationFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHttpClientTest {

    private HttpServer server;
    private AgentHttpClient client;
    private LearnFlowAiAgentProperties properties;
    private SdkTracerProvider tracerProvider;
    private OpenTelemetry openTelemetry;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        properties = new LearnFlowAiAgentProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setDefaultReadTimeout(Duration.ofSeconds(5));
        tracerProvider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        client = new AgentHttpClient(
                new OkHttpClient(),
                new ObjectMapper(),
                properties,
                new AgentResilienceManager(properties),
                openTelemetry,
                new SimpleMeterRegistry()
        );
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
        if (tracerProvider != null) {
            tracerProvider.close();
        }
    }

    @Test
    void appliesOperationBudgetHeaderAndParsesJson() {
        AtomicReference<String> timeoutHeader = new AtomicReference<>();
        server.createContext("/ok", exchange -> {
            timeoutHeader.set(exchange.getRequestHeaders().getFirst(AgentHttpClient.TIMEOUT_HEADER));
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });

        Map<?, ?> response = client.get(AgentOperation.RAG, "/ok", Map.class);

        assertEquals("ok", response.get("status"));
        long propagatedBudget = Long.parseLong(timeoutHeader.get());
        assertTrue(propagatedBudget > 0);
        assertTrue(propagatedBudget <= properties.getBudgets().getRag().toMillis());
        assertTrue(propagatedBudget >= properties.getBudgets().getRag().minusSeconds(1).toMillis());
    }

    @Test
    void propagatesW3cTraceContextAndBoundedRequestId() {
        AtomicReference<String> traceparent = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        server.createContext("/trace", exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            requestId.set(exchange.getRequestHeaders().getFirst(RequestCorrelationFilter.REQUEST_ID_HEADER));
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });
        Span parent = openTelemetry.getTracer("test").spanBuilder("browser-request").startSpan();

        try (
                Scope ignored = parent.makeCurrent();
                MDC.MDCCloseable requestScope = MDC.putCloseable(
                        RequestCorrelationFilter.REQUEST_ID_MDC_KEY,
                        "gateway-request-123"
                )
        ) {
            client.get(AgentOperation.RAG, "/trace", Map.class);
        } finally {
            parent.end();
        }

        assertTrue(traceparent.get().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
        assertEquals("gateway-request-123", requestId.get());
    }

    @Test
    void retriesOnlySafeGetRequests() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/eventual", exchange -> {
            if (calls.incrementAndGet() == 1) {
                sendJson(exchange, 503, "{\"error\":\"temporary\"}");
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        });

        Map<?, ?> response = client.get(AgentOperation.ADMIN, "/eventual", Map.class);

        assertEquals("ok", response.get("status"));
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryPostWithoutIdempotencyContract() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/write", exchange -> {
            calls.incrementAndGet();
            sendJson(exchange, 503, "{\"error\":\"temporary\"}");
        });

        AgentCallException exception = assertThrows(
                AgentCallException.class,
                () -> client.postJson(AgentOperation.PLAN, "/write", Map.of("goal", "test"), Map.class)
        );

        assertEquals(AgentCallException.Reason.HTTP_ERROR, exception.getReason());
        assertEquals(1, calls.get());
    }

    @Test
    void classifiesCallBudgetExpiryAsOverallTimeout() {
        properties.getBudgets().setAdmin(Duration.ofMillis(100));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(300);
                sendJson(exchange, 200, "{\"status\":\"late\"}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client is expected to close the exchange when its call budget expires.
            }
        });

        long startedAt = System.nanoTime();
        AgentCallException exception = assertThrows(
                AgentCallException.class,
                () -> client.get(AgentOperation.ADMIN, "/slow", Map.class)
        );

        assertEquals(AgentCallException.Reason.OVERALL_TIMEOUT, exception.getReason());
        assertTrue(Duration.ofNanos(System.nanoTime() - startedAt).toMillis() < 500);
    }

    @Test
    void cancelsDownstreamStreamWhenClientOutputDisconnects() {
        server.createContext("/stream", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write("data: hello\n\n".getBytes(StandardCharsets.UTF_8));
                body.flush();
            }
        });
        OutputStream disconnectedClient = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("browser disconnected");
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                throw new IOException("browser disconnected");
            }
        };

        IOException exception = assertThrows(
                IOException.class,
                () -> client.streamJson(
                        AgentOperation.STREAM,
                        "/stream",
                        Map.of("message", "hello"),
                        disconnectedClient
                )
        );

        assertEquals("Upstream client disconnected", exception.getMessage());
    }

    @Test
    void cancelsAnActiveDurableTaskCallByTaskId() throws Exception {
        UUID taskId = UUID.randomUUID();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        server.createContext("/task", exchange -> {
            requestStarted.countDown();
            try {
                releaseServer.await(2, TimeUnit.SECONDS);
                sendJson(exchange, 200, "{\"status\":\"late\"}");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Expected after the task call is cancelled.
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> call = executor.submit(() -> client.postJsonForTask(
                taskId,
                AgentOperation.PLAN,
                "/task",
                Map.of("goal", "test"),
                Map.class
        ));

        assertTrue(requestStarted.await(1, TimeUnit.SECONDS));
        assertTrue(client.cancelTask(taskId));
        releaseServer.countDown();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
        assertEquals(
                AgentCallException.Reason.CANCELLED,
                ((AgentCallException) failure.getCause()).getReason()
        );
        executor.shutdownNow();
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
