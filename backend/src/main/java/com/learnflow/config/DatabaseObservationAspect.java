package com.learnflow.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Adds bounded repository-level database spans without recording SQL arguments,
 * entity values, query text, or exception messages.
 */
@Aspect
@Component
public class DatabaseObservationAspect {

    private final Tracer tracer;

    public DatabaseObservationAspect(ObjectProvider<OpenTelemetry> provider) {
        OpenTelemetry openTelemetry = provider.getIfAvailable(OpenTelemetry::noop);
        this.tracer = openTelemetry.getTracer("com.learnflow.database");
    }

    @Around("execution(* com.learnflow.repository..*(..)) || execution(* com.learnflow.service.AsyncTaskLeaseService.*(..))")
    public Object traceDatabaseStage(ProceedingJoinPoint joinPoint) throws Throwable {
        String owner = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String operation = joinPoint.getSignature().getName();
        Span span = tracer.spanBuilder("db." + owner + "." + operation)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("db.system", "postgresql")
                .setAttribute("db.namespace", "learnflow")
                .setAttribute("db.operation.name", operation)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return joinPoint.proceed();
        } catch (Throwable failure) {
            span.setStatus(StatusCode.ERROR, failure.getClass().getSimpleName());
            span.setAttribute("error.type", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            span.end();
        }
    }
}
