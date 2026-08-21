package com.learnflow.service;

import com.learnflow.config.RequestCorrelationFilter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Captures and restores only W3C tracing metadata across the durable task boundary. */
@Component
public class TelemetryContext {

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public TelemetryContext(ObjectProvider<OpenTelemetry> provider) {
        this(provider.getIfAvailable(OpenTelemetry::noop));
    }

    TelemetryContext(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("com.learnflow.async-task");
    }

    public String captureTraceparent() {
        Map<String, String> carrier = new HashMap<>();
        openTelemetry.getPropagators().getTextMapPropagator().inject(
                Context.current(),
                carrier,
                Map::put
        );
        return carrier.get("traceparent");
    }

    public String captureRequestId() {
        return MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
    }

    public RestoredContext restore(String traceparent, String requestId) {
        Map<String, String> carrier = new HashMap<>();
        if (traceparent != null && !traceparent.isBlank()) {
            carrier.put("traceparent", traceparent);
        }
        Context extracted = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), carrier, MAP_GETTER);
        Scope scope = extracted.makeCurrent();
        MDC.MDCCloseable requestScope = requestId == null
                ? null
                : MDC.putCloseable(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, requestId);
        return () -> {
            if (requestScope != null) {
                requestScope.close();
            }
            scope.close();
        };
    }

    public Span startTaskSpan(String taskType) {
        return tracer.spanBuilder("async-task." + taskType.toLowerCase(java.util.Locale.ROOT))
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan();
    }

    public Span startInternalSpan(String name) {
        return tracer.spanBuilder(name).setSpanKind(SpanKind.INTERNAL).startSpan();
    }

    @FunctionalInterface
    public interface RestoredContext extends AutoCloseable {
        @Override
        void close();
    }
}
