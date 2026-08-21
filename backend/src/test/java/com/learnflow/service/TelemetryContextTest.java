package com.learnflow.service;

import com.learnflow.config.RequestCorrelationFilter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryContextTest {

    @Test
    void capturesAndRestoresOnlyW3cTraceAndBoundedRequestMetadata() {
        SdkTracerProvider provider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        TelemetryContext telemetry = new TelemetryContext(openTelemetry);
        Span parent = openTelemetry.getTracer("test").spanBuilder("submit-task").startSpan();
        String parentTraceId = parent.getSpanContext().getTraceId();
        String traceparent;
        String requestId;

        try (
                Scope ignored = parent.makeCurrent();
                MDC.MDCCloseable requestScope = MDC.putCloseable(
                        RequestCorrelationFilter.REQUEST_ID_MDC_KEY,
                        "gateway-request-456"
                )
        ) {
            traceparent = telemetry.captureTraceparent();
            requestId = telemetry.captureRequestId();
        } finally {
            parent.end();
        }

        assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(requestId).isEqualTo("gateway-request-456");
        try (TelemetryContext.RestoredContext ignored = telemetry.restore(traceparent, requestId)) {
            Span worker = telemetry.startTaskSpan("PLAN_GENERATION");
            assertThat(worker.getSpanContext().getTraceId()).isEqualTo(parentTraceId);
            assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY))
                    .isEqualTo("gateway-request-456");
            worker.end();
        } finally {
            provider.close();
        }
    }
}
