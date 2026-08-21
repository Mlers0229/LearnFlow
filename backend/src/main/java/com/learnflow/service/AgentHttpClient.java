package com.learnflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.config.LearnFlowAiAgentProperties;
import com.learnflow.config.RequestCorrelationFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PreDestroy;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AgentHttpClient {

    public static final String TIMEOUT_HEADER = "X-LearnFlow-Timeout-Ms";
    private static final Logger log = LoggerFactory.getLogger(AgentHttpClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient sharedClient;
    private final ObjectMapper objectMapper;
    private final LearnFlowAiAgentProperties properties;
    private final AgentResilienceManager resilienceManager;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final String baseUrl;
    private final ConcurrentMap<UUID, Call> activeTaskCalls = new ConcurrentHashMap<>();

    @Autowired
    public AgentHttpClient(
            OkHttpClient sharedClient,
            ObjectMapper objectMapper,
            LearnFlowAiAgentProperties properties,
            AgentResilienceManager resilienceManager,
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            MeterRegistry meterRegistry
    ) {
        this(
                sharedClient,
                objectMapper,
                properties,
                resilienceManager,
                openTelemetryProvider.getIfAvailable(OpenTelemetry::noop),
                meterRegistry
        );
    }

    AgentHttpClient(
            OkHttpClient sharedClient,
            ObjectMapper objectMapper,
            LearnFlowAiAgentProperties properties,
            AgentResilienceManager resilienceManager
    ) {
        this(
                sharedClient,
                objectMapper,
                properties,
                resilienceManager,
                OpenTelemetry.noop(),
                new SimpleMeterRegistry()
        );
    }

    AgentHttpClient(
            OkHttpClient sharedClient,
            ObjectMapper objectMapper,
            LearnFlowAiAgentProperties properties,
            AgentResilienceManager resilienceManager,
            OpenTelemetry openTelemetry,
            MeterRegistry meterRegistry
    ) {
        this.sharedClient = sharedClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.resilienceManager = resilienceManager;
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("com.learnflow.agent-client");
        this.meterRegistry = meterRegistry;
        this.baseUrl = stripTrailingSlash(properties.getBaseUrl());
    }

    public <T> T get(AgentOperation operation, String path, Class<T> responseType) {
        long deadlineNanos = deadlineFor(operation);
        return resilienceManager.execute(operation, true, () -> {
            Duration remaining = remainingBudget(operation, deadlineNanos);
            Request request = requestBuilder(path, remaining).get().build();
            return executeOnce(operation, request, responseType, deadlineNanos, null);
        });
    }

    public <T> T postJson(AgentOperation operation, String path, Object payload, Class<T> responseType) {
        return exchangeJson(operation, path, "POST", payload, responseType, null);
    }

    public <T> T postJsonForTask(
            UUID taskId,
            AgentOperation operation,
            String path,
            Object payload,
            Class<T> responseType
    ) {
        return exchangeJson(operation, path, "POST", payload, responseType, taskId);
    }

    public <T> T exchangeJson(
            AgentOperation operation,
            String path,
            String method,
            Object payload,
            Class<T> responseType
    ) {
        return exchangeJson(operation, path, method, payload, responseType, null);
    }

    private <T> T exchangeJson(
            AgentOperation operation,
            String path,
            String method,
            Object payload,
            Class<T> responseType,
            UUID taskId
    ) {
        String json = serialize(operation, payload);
        RequestBody body = RequestBody.create(json, JSON);
        long deadlineNanos = deadlineFor(operation);
        return resilienceManager.execute(operation, false, () -> {
            Duration remaining = remainingBudget(operation, deadlineNanos);
            Request request = requestBuilder(path, remaining)
                    .method(method, body)
                    .header("Accept", "application/json")
                    .build();
            return executeOnce(operation, request, responseType, deadlineNanos, taskId);
        });
    }

    public void streamJson(AgentOperation operation, String path, Object payload, OutputStream outputStream)
            throws IOException {
        String json = serialize(operation, payload);
        long deadlineNanos = deadlineFor(operation);
        try {
            resilienceManager.execute(operation, false, () -> {
                try {
                    streamOnce(operation, path, json, outputStream, deadlineNanos);
                    return null;
                } catch (IOException exception) {
                    throw new StreamExecutionException(exception);
                }
            });
        } catch (StreamExecutionException exception) {
            throw exception.ioCause();
        }
    }

    public Duration budgetFor(AgentOperation operation) {
        LearnFlowAiAgentProperties.Budgets budgets = properties.getBudgets();
        return switch (operation) {
            case ADMIN -> budgets.getAdmin();
            case RAG -> budgets.getRag();
            case TUTOR -> budgets.getTutor();
            case PLAN -> budgets.getPlan();
            case STREAM -> budgets.getStream();
        };
    }

    private <T> T executeOnce(
            AgentOperation operation,
            Request request,
            Class<T> responseType,
            long deadlineNanos,
            UUID taskId
    ) {
        Duration remaining = remainingBudget(operation, deadlineNanos);
        Span span = tracer.spanBuilder("agent." + operation.name().toLowerCase(Locale.ROOT))
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "http")
                .setAttribute("server.address", request.url().host())
                .setAttribute("http.request.method", request.method())
                .setAttribute("learnflow.ai.operation", operation.name().toLowerCase(Locale.ROOT))
                .startSpan();
        Request tracedRequest;
        try (Scope ignored = span.makeCurrent()) {
            tracedRequest = injectContext(request);
        }
        Call call = clientFor(operation, remaining).newCall(tracedRequest);
        if (taskId != null) {
            activeTaskCalls.put(taskId, call);
        }
        long startedAt = System.nanoTime();
        String outcome = "success";
        String reason = "none";
        try (Scope ignored = span.makeCurrent(); Response response = call.execute()) {
            span.setAttribute("http.response.status_code", response.code());
            if (!response.isSuccessful()) {
                throw httpError(operation, response.code());
            }
            ResponseBody responseBody = response.body();
            String raw = responseBody == null ? "" : responseBody.string();
            if (responseType == String.class) {
                return responseType.cast(raw);
            }
            if (raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, responseType);
        } catch (AgentCallException exception) {
            outcome = "failure";
            reason = exception.getReason().name().toLowerCase(Locale.ROOT);
            recordSpanFailure(span, exception, reason);
            logFailure(exception, startedAt);
            throw exception;
        } catch (JsonProcessingException exception) {
            AgentCallException mapped = new AgentCallException(
                    operation,
                    AgentCallException.Reason.SERIALIZATION_ERROR,
                    "Unable to parse Agent response",
                    exception,
                    null
            );
            outcome = "failure";
            reason = "serialization_error";
            recordSpanFailure(span, exception, reason);
            logFailure(mapped, startedAt);
            throw mapped;
        } catch (IOException exception) {
            AgentCallException mapped = mapIoException(operation, call, exception, deadlineNanos);
            outcome = "failure";
            reason = mapped.getReason().name().toLowerCase(Locale.ROOT);
            recordSpanFailure(span, exception, reason);
            logFailure(mapped, startedAt);
            throw mapped;
        } finally {
            recordCallMetrics(operation, outcome, reason, startedAt);
            span.end();
            if (taskId != null) {
                activeTaskCalls.remove(taskId, call);
            }
        }
    }

    public boolean cancelTask(UUID taskId) {
        Call call = activeTaskCalls.get(taskId);
        if (call == null) {
            return false;
        }
        call.cancel();
        return true;
    }

    private void streamOnce(
            AgentOperation operation,
            String path,
            String json,
            OutputStream outputStream,
            long deadlineNanos
    ) throws IOException {
        Duration remaining = remainingBudget(operation, deadlineNanos);
        Request request = requestBuilder(path, remaining)
                .post(RequestBody.create(json, JSON))
                .header("Accept", "text/event-stream")
                .build();
        Span span = tracer.spanBuilder("agent.stream")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "http")
                .setAttribute("server.address", request.url().host())
                .setAttribute("http.request.method", request.method())
                .setAttribute("learnflow.ai.operation", operation.name().toLowerCase(Locale.ROOT))
                .startSpan();
        Request tracedRequest;
        try (Scope ignored = span.makeCurrent()) {
            tracedRequest = injectContext(request);
        }
        Call call = clientFor(operation, remaining).newCall(tracedRequest);
        long startedAt = System.nanoTime();
        String outcome = "success";
        String reason = "none";
        try (Scope ignored = span.makeCurrent(); Response response = call.execute()) {
            span.setAttribute("http.response.status_code", response.code());
            if (!response.isSuccessful()) {
                throw httpError(operation, response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new AgentCallException(
                        operation,
                        AgentCallException.Reason.IO_ERROR,
                        "Agent streaming response has no body",
                        null,
                        response.code()
                );
            }
            copyStream(responseBody.byteStream(), outputStream, call);
        } catch (ClientDisconnectedException exception) {
            outcome = "cancelled";
            reason = "upstream_disconnected";
            recordSpanFailure(span, exception, reason);
            call.cancel();
            log.info("Agent stream cancelled because the upstream client disconnected operation={}", operation);
            throw exception;
        } catch (AgentCallException exception) {
            outcome = "failure";
            reason = exception.getReason().name().toLowerCase(Locale.ROOT);
            recordSpanFailure(span, exception, reason);
            logFailure(exception, startedAt);
            throw exception;
        } catch (IOException exception) {
            call.cancel();
            AgentCallException mapped = mapIoException(operation, call, exception, deadlineNanos);
            outcome = "failure";
            reason = mapped.getReason().name().toLowerCase(Locale.ROOT);
            recordSpanFailure(span, exception, reason);
            logFailure(mapped, startedAt);
            throw mapped;
        } finally {
            recordCallMetrics(operation, outcome, reason, startedAt);
            span.end();
        }
    }

    private OkHttpClient clientFor(AgentOperation operation, Duration remaining) {
        Duration readTimeout = operation == AgentOperation.STREAM
                ? min(properties.getBudgets().getStreamIdle(), remaining)
                : min(properties.getDefaultReadTimeout(), remaining);
        return sharedClient.newBuilder()
                .readTimeout(readTimeout)
                .callTimeout(remaining)
                .build();
    }

    private Request.Builder requestBuilder(String path, Duration remaining) {
        return new Request.Builder()
                .url(resolveUrl(path))
                .header(TIMEOUT_HEADER, Long.toString(remaining.toMillis()));
    }

    private Request injectContext(Request request) {
        Request.Builder builder = request.newBuilder();
        openTelemetry.getPropagators().getTextMapPropagator().inject(
                Context.current(),
                builder,
                (carrier, key, value) -> carrier.header(key, value)
        );
        String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
        if (requestId != null) {
            builder.header(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId);
            Span.current().setAttribute("learnflow.request.id", requestId);
        }
        return builder.build();
    }

    private static void recordSpanFailure(Span span, Throwable failure, String reason) {
        span.setStatus(StatusCode.ERROR, reason);
        span.setAttribute("error.type", failure.getClass().getSimpleName());
        span.setAttribute("learnflow.degradation.reason", reason);
    }

    private void recordCallMetrics(AgentOperation operation, String outcome, String reason, long startedAt) {
        Tags tags = Tags.of(
                "operation", operation.name().toLowerCase(Locale.ROOT),
                "outcome", outcome,
                "reason", reason
        );
        meterRegistry.counter("learnflow.ai.agent.calls", tags).increment();
        meterRegistry.timer("learnflow.ai.agent.duration", tags)
                .record(Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private String serialize(AgentOperation operation, Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new AgentCallException(
                    operation,
                    AgentCallException.Reason.SERIALIZATION_ERROR,
                    "Unable to serialize Agent request",
                    exception,
                    null
            );
        }
    }

    private AgentCallException mapIoException(
            AgentOperation operation,
            Call call,
            IOException exception,
            long deadlineNanos
    ) {
        AgentCallException.Reason reason;
        String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
        if (System.nanoTime() + Duration.ofMillis(50).toNanos() >= deadlineNanos) {
            reason = AgentCallException.Reason.OVERALL_TIMEOUT;
        } else if (exception instanceof ConnectException || message.contains("connect timed out")) {
            reason = AgentCallException.Reason.CONNECT_TIMEOUT;
        } else if (exception instanceof SocketTimeoutException) {
            reason = AgentCallException.Reason.READ_TIMEOUT;
        } else if (call.isCanceled()) {
            reason = AgentCallException.Reason.CANCELLED;
        } else {
            reason = AgentCallException.Reason.IO_ERROR;
        }
        return new AgentCallException(operation, reason, "Agent call failed: " + reason, exception, null);
    }

    private long deadlineFor(AgentOperation operation) {
        return System.nanoTime() + budgetFor(operation).toNanos();
    }

    private Duration remainingBudget(AgentOperation operation, long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= Duration.ofMillis(1).toNanos()) {
            throw new AgentCallException(
                    operation,
                    AgentCallException.Reason.OVERALL_TIMEOUT,
                    "Agent call exhausted its overall budget",
                    null,
                    null
            );
        }
        return Duration.ofNanos(remainingNanos);
    }

    private static AgentCallException httpError(AgentOperation operation, int statusCode) {
        return new AgentCallException(
                operation,
                AgentCallException.Reason.HTTP_ERROR,
                "Agent returned HTTP " + statusCode,
                null,
                statusCode
        );
    }

    private static void copyStream(InputStream inputStream, OutputStream outputStream, Call call) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            try {
                outputStream.write(buffer, 0, read);
                outputStream.flush();
            } catch (IOException clientDisconnected) {
                call.cancel();
                throw new ClientDisconnectedException(clientDisconnected);
            }
        }
    }

    private void logFailure(AgentCallException exception, long startedAt) {
        log.warn(
                "Agent call failed operation={} reason={} status={} durationMs={}",
                exception.getOperation(),
                exception.getReason(),
                exception.getStatusCode(),
                elapsedMillis(startedAt)
        );
    }

    private String resolveUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static final class ClientDisconnectedException extends IOException {
        private ClientDisconnectedException(IOException cause) {
            super("Upstream client disconnected", cause);
        }
    }

    private static final class StreamExecutionException extends RuntimeException {
        private StreamExecutionException(IOException cause) {
            super(cause);
        }

        private IOException ioCause() {
            return (IOException) getCause();
        }
    }

    @PreDestroy
    public void close() {
        sharedClient.dispatcher().cancelAll();
        sharedClient.connectionPool().evictAll();
        sharedClient.dispatcher().executorService().shutdown();
    }
}
