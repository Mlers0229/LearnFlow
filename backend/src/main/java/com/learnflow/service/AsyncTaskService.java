package com.learnflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.config.LearnFlowTaskProperties;
import com.learnflow.dto.AsyncTaskResponse;
import com.learnflow.dto.GoalRequest;
import com.learnflow.entity.AsyncTask;
import com.learnflow.repository.AsyncTaskRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AsyncTaskService {

    public static final String PLAN_GENERATION = "PLAN_GENERATION";
    public static final String RESOURCE_INGESTION = "RESOURCE_INGESTION";
    public static final String RESOURCE_EMBEDDING = "RESOURCE_EMBEDDING";
    private static final int MAX_PAYLOAD_BYTES = 32_768;

    private final AsyncTaskRepository repository;
    private final ObjectMapper objectMapper;
    private final LearnFlowTaskProperties properties;
    private final AgentHttpClient agentHttpClient;
    private final TelemetryContext telemetryContext;
    private final PlanWorkflowStateService planWorkflowStateService;

    @Autowired
    public AsyncTaskService(
            AsyncTaskRepository repository,
            ObjectMapper objectMapper,
            LearnFlowTaskProperties properties,
            AgentHttpClient agentHttpClient,
            TelemetryContext telemetryContext,
            PlanWorkflowStateService planWorkflowStateService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.agentHttpClient = agentHttpClient;
        this.telemetryContext = telemetryContext;
        this.planWorkflowStateService = planWorkflowStateService;
    }

    AsyncTaskService(
            AsyncTaskRepository repository,
            ObjectMapper objectMapper,
            LearnFlowTaskProperties properties,
            AgentHttpClient agentHttpClient
    ) {
        this(repository, objectMapper, properties, agentHttpClient, new TelemetryContext(io.opentelemetry.api.OpenTelemetry.noop()), null);
    }


    AsyncTaskService(
            AsyncTaskRepository repository,
            ObjectMapper objectMapper,
            LearnFlowTaskProperties properties,
            AgentHttpClient agentHttpClient,
            PlanWorkflowStateService planWorkflowStateService
    ) {
        this(
                repository, objectMapper, properties, agentHttpClient,
                new TelemetryContext(io.opentelemetry.api.OpenTelemetry.noop()),
                planWorkflowStateService
        );
    }
    @Transactional
    public AsyncTaskResponse createPlanTask(GoalRequest request, Long userId, String requestedIdempotencyKey) {
        String idempotencyKey = normalizeIdempotencyKey(requestedIdempotencyKey);
        request.setUserId(userId);
        String payload = serializeRequest(request);
        String fingerprint = sha256(payload);
        AsyncTask existing = repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(
                userId,
                PLAN_GENERATION,
                idempotencyKey
        ).orElse(null);
        if (existing != null) {
            if (!fingerprint.equals(existing.getRequestFingerprint())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key 已用于不同的任务参数");
            }
            return toResponse(existing);
        }

        OffsetDateTime now = now();
        AsyncTask task = new AsyncTask();
        task.setId(UUID.randomUUID());
        task.setTaskType(PLAN_GENERATION);
        task.setOwnerUserId(userId);
        task.setIdempotencyKey(idempotencyKey);
        task.setRequestFingerprint(fingerprint);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setRequestPayload(payload);
        task.setTraceparent(telemetryContext.captureTraceparent());
        task.setRequestId(telemetryContext.captureRequestId());
        task.setAttemptCount(0);
        task.setMaxAttempts(boundedMaxAttempts());
        task.setNextAttemptAt(now);
        task.setDeadlineAt(now.plus(properties.getTaskTimeout()));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try {
            return toResponse(repository.saveAndFlush(task));
        } catch (DataIntegrityViolationException race) {
            AsyncTask racedTask = repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(
                    userId,
                    PLAN_GENERATION,
                    idempotencyKey
            ).orElseThrow(() -> race);
            if (!fingerprint.equals(racedTask.getRequestFingerprint())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key 已用于不同的任务参数");
            }
            return toResponse(racedTask);
        }
    }

    @Transactional
    public TaskSubmission createResourceIngestionTask(
            UUID ingestionId,
            Long resourceId,
            Long userId,
            String requestedIdempotencyKey,
            String requestFingerprint
    ) {
        String idempotencyKey = normalizeIdempotencyKey(requestedIdempotencyKey);
        AsyncTask existing = repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(
                userId, RESOURCE_INGESTION, idempotencyKey
        ).orElse(null);
        if (existing != null) {
            assertSameFingerprint(existing, requestFingerprint);
            return new TaskSubmission(toResponse(existing), ingestionIdFrom(existing), false);
        }

        IngestionTaskPayload taskPayload = new IngestionTaskPayload(ingestionId, resourceId);
        String payload = serializePayload(taskPayload);
        OffsetDateTime timestamp = now();
        AsyncTask task = new AsyncTask();
        task.setId(UUID.randomUUID());
        task.setTaskType(RESOURCE_INGESTION);
        task.setOwnerUserId(userId);
        task.setIdempotencyKey(idempotencyKey);
        task.setRequestFingerprint(requestFingerprint);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setRequestPayload(payload);
        task.setTraceparent(telemetryContext.captureTraceparent());
        task.setRequestId(telemetryContext.captureRequestId());
        task.setAttemptCount(0);
        task.setMaxAttempts(boundedMaxAttempts());
        task.setNextAttemptAt(timestamp);
        task.setDeadlineAt(timestamp.plus(properties.getTaskTimeout()));
        task.setCreatedAt(timestamp);
        task.setUpdatedAt(timestamp);
        try {
            AsyncTask saved = repository.saveAndFlush(task);
            return new TaskSubmission(toResponse(saved), ingestionId, true);
        } catch (DataIntegrityViolationException race) {
            AsyncTask raced = repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(
                    userId, RESOURCE_INGESTION, idempotencyKey
            ).orElseThrow(() -> race);
            assertSameFingerprint(raced, requestFingerprint);
            return new TaskSubmission(toResponse(raced), ingestionIdFrom(raced), false);
        }
    }

    @Transactional
    public AsyncTaskResponse createResourceEmbeddingTask(
            UUID ingestionId,
            Long resourceId,
            Long userId,
            String embeddingVersion
    ) {
        String idempotencyKey = "embedding:" + ingestionId + ":" + embeddingVersion;
        String fingerprint = sha256(ingestionId + "|" + resourceId + "|" + embeddingVersion);
        AsyncTask existing = repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(
                userId, RESOURCE_EMBEDDING, idempotencyKey
        ).orElse(null);
        if (existing != null) {
            assertSameFingerprint(existing, fingerprint);
            return toResponse(existing);
        }

        OffsetDateTime timestamp = now();
        AsyncTask task = new AsyncTask();
        task.setId(UUID.randomUUID());
        task.setTaskType(RESOURCE_EMBEDDING);
        task.setOwnerUserId(userId);
        task.setIdempotencyKey(idempotencyKey);
        task.setRequestFingerprint(fingerprint);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setRequestPayload(serializePayload(new EmbeddingTaskPayload(
                ingestionId, resourceId, embeddingVersion
        )));
        task.setTraceparent(telemetryContext.captureTraceparent());
        task.setRequestId(telemetryContext.captureRequestId());
        task.setAttemptCount(0);
        task.setMaxAttempts(boundedMaxAttempts());
        task.setNextAttemptAt(timestamp);
        task.setDeadlineAt(timestamp.plus(properties.getTaskTimeout()));
        task.setCreatedAt(timestamp);
        task.setUpdatedAt(timestamp);
        try {
            return toResponse(repository.saveAndFlush(task));
        } catch (DataIntegrityViolationException race) {
            AsyncTask raced = repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(
                    userId, RESOURCE_EMBEDDING, idempotencyKey
            ).orElseThrow(() -> race);
            assertSameFingerprint(raced, fingerprint);
            return toResponse(raced);
        }
    }

    @Transactional(readOnly = true)
    public AsyncTaskResponse getForUser(UUID taskId, Long userId) {
        return toResponse(requireOwnedTask(taskId, userId));
    }

    @Transactional
    public AsyncTaskResponse cancelForUser(UUID taskId, Long userId) {
        AsyncTask task = requireOwnedTask(taskId, userId);
        if (isTerminal(task.getStatus())) {
            return toResponse(task);
        }
        OffsetDateTime now = now();
        task.setCancelRequestedAt(now);
        task.setUpdatedAt(now);
        if ("PENDING".equals(task.getStatus()) || "PAUSED".equals(task.getStatus())) {
            transitionToCancelled(task, now);
        } else if ("RUNNING".equals(task.getStatus())
                && (PLAN_GENERATION.equals(task.getTaskType())
                || RESOURCE_EMBEDDING.equals(task.getTaskType()))) {
            agentHttpClient.cancelTask(taskId);
        }
        AsyncTask saved = repository.save(task);
        if (planWorkflowStateService != null && PLAN_GENERATION.equals(task.getTaskType())) {
            planWorkflowStateService.markCancelled(taskId);
        }
        return toResponse(saved);
    }

    public AsyncTaskResponse pauseForUser(UUID taskId, Long userId) {
        AsyncTask task = requireOwnedTask(taskId, userId);
        if (!PLAN_GENERATION.equals(task.getTaskType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有计划生成任务支持暂停");
        }
        if ("PAUSED".equals(task.getStatus())) {
            if (planWorkflowStateService != null) {
                planWorkflowStateService.markPaused(taskId);
            }
            return toResponse(task);
        }
        if (isTerminal(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "终态任务不能暂停");
        }

        boolean wasRunning = "RUNNING".equals(task.getStatus());
        OffsetDateTime timestamp = now();
        task.setPauseRequestedAt(timestamp);
        task.setStatus("PAUSED");
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(timestamp);
        if (wasRunning) {
            task.setAttemptCount(Math.max(0, task.getAttemptCount() - 1));
        }
        AsyncTask saved = repository.save(task);
        if (planWorkflowStateService != null) {
            planWorkflowStateService.markPaused(taskId);
        }
        if (wasRunning) {
            agentHttpClient.cancelTask(taskId);
        }
        return toResponse(saved);
    }

    @Transactional
    public AsyncTaskResponse resumeForUser(UUID taskId, Long userId) {
        AsyncTask task = requireOwnedTask(taskId, userId);
        if (!PLAN_GENERATION.equals(task.getTaskType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有计划生成任务支持继续");
        }
        if (!"PAUSED".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已暂停任务可以继续");
        }
        if (task.getRequestPayload() == null || task.getRequestPayload().isBlank()) {
            throw new ResponseStatusException(HttpStatus.GONE, "任务内容已清理，不能继续");
        }

        OffsetDateTime timestamp = now();
        if (planWorkflowStateService != null) {
            planWorkflowStateService.markResumed(taskId);
        }
        task.setStatus("PENDING");
        task.setPauseRequestedAt(null);
        task.setNextAttemptAt(timestamp);
        task.setDeadlineAt(timestamp.plus(properties.getTaskTimeout()));
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setFinishedAt(null);
        task.setErrorCode(null);
        task.setErrorSummary(null);
        task.setUpdatedAt(timestamp);
        return toResponse(repository.save(task));
    }

    @Transactional(readOnly = true)
    public AsyncTask load(UUID taskId) {
        return repository.findById(taskId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public boolean isCancellationRequested(UUID taskId) {
        AsyncTask task = repository.findById(taskId).orElseThrow();
        return task.getCancelRequestedAt() != null || "CANCELLED".equals(task.getStatus());
    }

    @Transactional(readOnly = true)
    public boolean isPauseRequested(UUID taskId) {
        AsyncTask task = repository.findById(taskId).orElseThrow();
        return task.getPauseRequestedAt() != null || "PAUSED".equals(task.getStatus());
    }

    @Transactional(readOnly = true)
    public boolean isDeadlineExceeded(UUID taskId) {
        AsyncTask task = repository.findById(taskId).orElseThrow();
        return !task.getDeadlineAt().isAfter(now());
    }

    @Transactional
    public void updateProgress(UUID taskId, int progress) {
        AsyncTask task = repository.findById(taskId).orElseThrow();
        if (!"RUNNING".equals(task.getStatus())) {
            return;
        }
        task.setProgress(Math.max(task.getProgress(), Math.min(99, progress)));
        task.setUpdatedAt(now());
        repository.save(task);
    }

    @Transactional
    public void complete(UUID taskId, Long planId) {
        complete(taskId, "STUDY_PLAN", planId);
    }

    @Transactional
    public void complete(UUID taskId, String resourceType, Long resourceId) {
        AsyncTask task = repository.findById(taskId).orElseThrow();
        if (!"RUNNING".equals(task.getStatus())) {
            return;
        }
        OffsetDateTime now = now();
        task.setStatus("SUCCEEDED");
        task.setProgress(100);
        task.setResultResourceType(resourceType);
        task.setResultResourceId(resourceId);
        task.setRequestPayload(null);
        task.setPauseRequestedAt(null);
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setErrorCode(null);
        task.setErrorSummary(null);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        repository.save(task);
    }

    @Transactional
    public void cancelRunning(UUID taskId) {
        AsyncTask task = repository.findById(taskId).orElseThrow();
        if (isTerminal(task.getStatus())) {
            return;
        }
        transitionToCancelled(task, now());
        repository.save(task);
        if (planWorkflowStateService != null && PLAN_GENERATION.equals(task.getTaskType())) {
            planWorkflowStateService.markCancelled(taskId);
        }
    }

    @Transactional
    public void failOrRetry(UUID taskId, Throwable failure) {
        AsyncTask task = repository.findById(taskId).orElseThrow();
        OffsetDateTime now = now();
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setErrorCode(errorCode(failure));
        task.setErrorSummary("任务执行失败；详细异常仅保留在受控服务日志中");
        task.setUpdatedAt(now);

        if (task.getCancelRequestedAt() != null) {
            transitionToCancelled(task, now);
        } else if (task.getPauseRequestedAt() != null || "PAUSED".equals(task.getStatus())) {
            transitionToPaused(task, now);
        } else if (task.getAttemptCount() >= task.getMaxAttempts() || !task.getDeadlineAt().isAfter(now)) {
            task.setStatus("FAILED");
            task.setFinishedAt(now);
        } else {
            task.setStatus("PENDING");
            task.setProgress(Math.min(task.getProgress(), 20));
            task.setNextAttemptAt(now.plus(retryDelay(task.getAttemptCount())));
        }
        repository.save(task);
        if ("CANCELLED".equals(task.getStatus()) && planWorkflowStateService != null) {
            planWorkflowStateService.markCancelled(taskId);
        }
    }

    @Transactional(readOnly = true)
    public List<AsyncTaskResponse> failedTasks() {
        int pageSize = Math.max(1, Math.min(200, properties.getFailedPageSize()));
        return repository.findByStatusOrderByCreatedAtAsc("FAILED", PageRequest.of(0, pageSize))
                .stream()
                .map(AsyncTaskService::toResponse)
                .toList();
    }

    @Transactional
    public AsyncTaskResponse replayFailed(UUID taskId) {
        AsyncTask task = repository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        if (!"FAILED".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有失败任务可以重放");
        }
        if (task.getRequestPayload() == null || task.getRequestPayload().isBlank()) {
            throw new ResponseStatusException(HttpStatus.GONE, "任务内容已按保留策略清理，不能重放");
        }
        OffsetDateTime now = now();
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setAttemptCount(0);
        task.setNextAttemptAt(now);
        task.setDeadlineAt(now.plus(properties.getTaskTimeout()));
        task.setCancelRequestedAt(null);
        task.setPauseRequestedAt(null);
        task.setFinishedAt(null);
        task.setErrorCode(null);
        task.setErrorSummary(null);
        task.setUpdatedAt(now);
        return toResponse(repository.save(task));
    }

    private AsyncTask requireOwnedTask(UUID taskId, Long userId) {
        return repository.findByIdAndOwnerUserId(taskId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private String serializeRequest(GoalRequest request) {
        return serializePayload(request);
    }

    private String serializePayload(Object request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "任务参数过大");
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务参数无法序列化", exception);
        }
    }

    private void assertSameFingerprint(AsyncTask task, String fingerprint) {
        if (!fingerprint.equals(task.getRequestFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key 已用于不同的任务参数");
        }
    }

    private UUID ingestionIdFrom(AsyncTask task) {
        if (task.getRequestPayload() == null || task.getRequestPayload().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(task.getRequestPayload(), IngestionTaskPayload.class).ingestionId();
        } catch (Exception failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "既有摄取任务缺少可恢复的资源引用");
        }
    }

    private String normalizeIdempotencyKey(String requested) {
        if (requested == null || requested.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String value = requested.trim();
        if (value.length() > 128 || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key 格式无效");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private int boundedMaxAttempts() {
        return Math.max(1, Math.min(10, properties.getMaxAttempts()));
    }

    private Duration retryDelay(int attemptCount) {
        long multiplier = 1L << Math.min(5, Math.max(0, attemptCount - 1));
        return properties.getRetryBaseDelay().multipliedBy(multiplier);
    }

    private static void transitionToCancelled(AsyncTask task, OffsetDateTime now) {
        task.setStatus("CANCELLED");
        task.setRequestPayload(null);
        task.setPauseRequestedAt(null);
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
    }

    private static void transitionToPaused(AsyncTask task, OffsetDateTime now) {
        task.setStatus("PAUSED");
        task.setLeaseOwner(null);
        task.setLeaseExpiresAt(null);
        task.setFinishedAt(null);
        task.setErrorCode(null);
        task.setErrorSummary(null);
        task.setUpdatedAt(now);
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    private static String errorCode(Throwable failure) {
        String simpleName = failure.getClass().getSimpleName();
        if (simpleName == null || simpleName.isBlank()) {
            return "TASK_EXECUTION_ERROR";
        }
        return simpleName.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT).substring(
                0,
                Math.min(64, simpleName.length())
        );
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static AsyncTaskResponse toResponse(AsyncTask task) {
        return new AsyncTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getStatus(),
                task.getProgress(),
                task.getAttemptCount(),
                task.getMaxAttempts(),
                task.getResultResourceType(),
                task.getResultResourceId(),
                task.getErrorCode(),
                task.getErrorSummary(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getUpdatedAt()
        );
    }

    public record IngestionTaskPayload(UUID ingestionId, Long resourceId) {}
    public record EmbeddingTaskPayload(UUID ingestionId, Long resourceId, String embeddingVersion) {}
    public record TaskSubmission(AsyncTaskResponse task, UUID ingestionId, boolean created) {}
}
