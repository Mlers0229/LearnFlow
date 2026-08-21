package com.learnflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.config.LearnFlowTaskProperties;
import com.learnflow.dto.GoalRequest;
import com.learnflow.dto.PlanResponse;
import com.learnflow.entity.AsyncTask;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AsyncTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskWorker.class);

    private final LearnFlowTaskProperties properties;
    private final AsyncTaskLeaseService leaseService;
    private final AsyncTaskService taskService;
    private final AiProxyService aiProxyService;
    private final PlanPersistenceService planPersistenceService;
    private final ResourceIngestionService resourceIngestionService;
    private final ResourceEmbeddingService resourceEmbeddingService;
    private final ObjectMapper objectMapper;
    private final TelemetryContext telemetryContext;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor;
    private final Semaphore capacity;
    private final String workerId;

    @Autowired
    public AsyncTaskWorker(
            LearnFlowTaskProperties properties,
            AsyncTaskLeaseService leaseService,
            AsyncTaskService taskService,
            AiProxyService aiProxyService,
            PlanPersistenceService planPersistenceService,
            ResourceIngestionService resourceIngestionService,
            ResourceEmbeddingService resourceEmbeddingService,
            ObjectMapper objectMapper,
            TelemetryContext telemetryContext,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.leaseService = leaseService;
        this.taskService = taskService;
        this.aiProxyService = aiProxyService;
        this.planPersistenceService = planPersistenceService;
        this.resourceIngestionService = resourceIngestionService;
        this.resourceEmbeddingService = resourceEmbeddingService;
        this.objectMapper = objectMapper;
        this.telemetryContext = telemetryContext;
        this.meterRegistry = meterRegistry;
        int concurrency = Math.max(1, Math.min(16, properties.getConcurrency()));
        this.capacity = new Semaphore(concurrency);
        Gauge.builder("learnflow.async.worker.available", capacity, Semaphore::availablePermits)
                .description("Available asynchronous worker execution slots")
                .register(meterRegistry);
        Gauge.builder("learnflow.async.worker.capacity", () -> concurrency)
                .description("Configured asynchronous worker execution slots")
                .strongReference(true)
                .register(meterRegistry);
        this.executor = Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("learnflow-task-worker");
            thread.setDaemon(false);
            return thread;
        });
        this.workerId = resolveWorkerId();
    }

    AsyncTaskWorker(
            LearnFlowTaskProperties properties,
            AsyncTaskLeaseService leaseService,
            AsyncTaskService taskService,
            AiProxyService aiProxyService,
            PlanPersistenceService planPersistenceService,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                leaseService,
                taskService,
                aiProxyService,
                planPersistenceService,
                null,
                null,
                objectMapper,
                new TelemetryContext(io.opentelemetry.api.OpenTelemetry.noop()),
                new SimpleMeterRegistry()
        );
    }

    @Scheduled(fixedDelayString = "${learnflow.tasks.poll-interval-ms:500}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        while (capacity.tryAcquire()) {
            Optional<UUID> claimed = leaseService.claimNext(workerId);
            if (claimed.isEmpty()) {
                capacity.release();
                return;
            }
            UUID taskId = claimed.get();
            executor.submit(() -> {
                try {
                    execute(taskId);
                } finally {
                    capacity.release();
                }
            });
        }
    }

    void execute(UUID taskId) {
        long startedAt = System.nanoTime();
        String outcome = "success";
        String reason = "none";
        String taskType = "unknown";
        Span span = null;
        try {
            AsyncTask task = taskService.load(taskId);
            taskType = task.getTaskType();
            try (TelemetryContext.RestoredContext ignored = telemetryContext.restore(task.getTraceparent(), task.getRequestId())) {
                span = telemetryContext.startTaskSpan(task.getTaskType());
                span.setAttribute("learnflow.task.type", task.getTaskType());
                span.setAttribute("learnflow.task.attempt", task.getAttemptCount());
                try (Scope spanScope = span.makeCurrent()) {
                    executeTask(taskId, task);
                }
            }
        } catch (Exception failure) {
            outcome = "failure";
            reason = failure.getClass().getSimpleName();
            if (span != null) {
                span.setStatus(StatusCode.ERROR, reason);
                span.setAttribute("error.type", reason);
            }
            log.warn(
                    "Async task attempt failed taskId={} errorType={}",
                    taskId,
                    failure.getClass().getSimpleName()
            );
            taskService.failOrRetry(taskId, failure);
        } finally {
            if (span != null) {
                span.end();
            }
            Tags tags = Tags.of("task.type", taskType, "outcome", outcome, "reason", reason);
            meterRegistry.counter("learnflow.async.tasks", tags).increment();
            meterRegistry.timer("learnflow.async.task.duration", tags)
                    .record(java.time.Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    private void executeTask(UUID taskId, AsyncTask task) throws Exception {
        if (taskService.isCancellationRequested(taskId)) {
            taskService.cancelRunning(taskId);
            return;
        }
        if (taskService.isDeadlineExceeded(taskId)) {
            throw new TimeoutException("Async task deadline exceeded before execution");
        }
        if (AsyncTaskService.RESOURCE_INGESTION.equals(task.getTaskType())) {
            executeResourceIngestion(taskId, task);
            return;
        }
        if (AsyncTaskService.RESOURCE_EMBEDDING.equals(task.getTaskType())) {
            executeResourceEmbedding(taskId, task);
            return;
        }
        if (!AsyncTaskService.PLAN_GENERATION.equals(task.getTaskType())) {
            throw new IllegalStateException("Unsupported task type");
        }

        taskService.updateProgress(taskId, 10);
        GoalRequest request = objectMapper.readValue(task.getRequestPayload(), GoalRequest.class);
        PlanResponse draft = aiProxyService.generatePlanDraft(request, taskId);
        taskService.updateProgress(taskId, 70);

        if (taskService.isCancellationRequested(taskId)) {
            taskService.cancelRunning(taskId);
            return;
        }
        if (taskService.isDeadlineExceeded(taskId)) {
            throw new TimeoutException("Async task deadline exceeded before persistence");
        }

        Long planId;
        Span persistenceSpan = telemetryContext.startInternalSpan("db.study-plan.persist");
        persistenceSpan.setAttribute("db.system", "postgresql");
        persistenceSpan.setAttribute("db.operation.name", "persist-study-plan");
        try (Scope ignored = persistenceSpan.makeCurrent()) {
            planId = planPersistenceService.persist(request, draft, taskId);
        } catch (Exception failure) {
            persistenceSpan.setStatus(StatusCode.ERROR, failure.getClass().getSimpleName());
            persistenceSpan.setAttribute("error.type", failure.getClass().getSimpleName());
            throw failure;
        } finally {
            persistenceSpan.end();
        }
        taskService.complete(taskId, planId);
        log.info("Async task completed taskId={} type={} resultResourceId={}", taskId, task.getTaskType(), planId);
    }

    private void executeResourceIngestion(UUID taskId, AsyncTask task) throws Exception {
        if (resourceIngestionService == null) throw new IllegalStateException("Resource ingestion service is unavailable");
        taskService.updateProgress(taskId, 10);
        AsyncTaskService.IngestionTaskPayload payload = objectMapper.readValue(
                task.getRequestPayload(), AsyncTaskService.IngestionTaskPayload.class
        );
        taskService.updateProgress(taskId, 25);
        long resourceId = resourceIngestionService.process(payload.ingestionId());
        if (taskService.isCancellationRequested(taskId)) {
            taskService.cancelRunning(taskId);
            return;
        }
        if (taskService.isDeadlineExceeded(taskId)) {
            throw new TimeoutException("Resource ingestion deadline exceeded before completion");
        }
        taskService.updateProgress(taskId, 90);
        if (resourceEmbeddingService != null && resourceEmbeddingService.isEnabled()) {
            resourceEmbeddingService.enqueueForIngestion(
                    payload.ingestionId(), resourceId, task.getOwnerUserId()
            );
        }
        taskService.complete(taskId, "RESOURCE_BANK", resourceId);
        log.info("Async resource ingestion completed taskId={} resourceId={}", taskId, resourceId);
    }

    private void executeResourceEmbedding(UUID taskId, AsyncTask task) throws Exception {
        if (resourceEmbeddingService == null) {
            throw new IllegalStateException("Resource embedding service is unavailable");
        }
        taskService.updateProgress(taskId, 10);
        AsyncTaskService.EmbeddingTaskPayload payload = objectMapper.readValue(
                task.getRequestPayload(), AsyncTaskService.EmbeddingTaskPayload.class
        );
        long resourceId = resourceEmbeddingService.process(
                taskId, payload.ingestionId(), payload.embeddingVersion()
        );
        if (taskService.isCancellationRequested(taskId)) {
            taskService.cancelRunning(taskId);
            return;
        }
        if (taskService.isDeadlineExceeded(taskId)) {
            throw new TimeoutException("Resource embedding deadline exceeded before completion");
        }
        taskService.complete(taskId, "RESOURCE_EMBEDDING", resourceId);
        log.info("Async resource embedding completed taskId={} resourceId={}", taskId, resourceId);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static String resolveWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception ignored) {
            return "learnflow:" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
