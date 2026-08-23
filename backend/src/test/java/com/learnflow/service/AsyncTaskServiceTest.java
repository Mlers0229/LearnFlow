package com.learnflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.config.LearnFlowTaskProperties;
import com.learnflow.dto.AsyncTaskResponse;
import com.learnflow.dto.GoalRequest;
import com.learnflow.entity.AsyncTask;
import com.learnflow.repository.AsyncTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.web.server.ResponseStatusException;

class AsyncTaskServiceTest {

    private AsyncTaskRepository repository;
    private LearnFlowTaskProperties properties;
    private AsyncTaskService service;
    private AgentHttpClient agentHttpClient;
    private PlanWorkflowStateService planWorkflowStateService;

    @BeforeEach
    void setUp() {
        repository = mock(AsyncTaskRepository.class);
        properties = new LearnFlowTaskProperties();
        agentHttpClient = mock(AgentHttpClient.class);
        planWorkflowStateService = mock(PlanWorkflowStateService.class);
        service = new AsyncTaskService(repository, new ObjectMapper(), properties, agentHttpClient, planWorkflowStateService);
        when(repository.save(any(AsyncTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveAndFlush(any(AsyncTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createUsesTrustedUserAndDeduplicatesByKey() {
        GoalRequest request = validGoal();
        request.setUserId(999L);
        when(repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(7L, "PLAN_GENERATION", "request-1"))
                .thenReturn(Optional.empty());

        AsyncTaskResponse created = service.createPlanTask(request, 7L, "request-1");

        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.maxAttempts()).isEqualTo(3);
        assertThat(request.getUserId()).isEqualTo(7L);
        verify(repository).saveAndFlush(any(AsyncTask.class));
    }

    @Test
    void pendingCancellationIsTerminalAndClearsPayload() {
        AsyncTask task = runningTask("PENDING", 0, 3);
        when(repository.findByIdAndOwnerUserId(task.getId(), 7L)).thenReturn(Optional.of(task));

        AsyncTaskResponse response = service.cancelForUser(task.getId(), 7L);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(task.getRequestPayload()).isNull();
        assertThat(task.getFinishedAt()).isNotNull();
        verify(planWorkflowStateService).markCancelled(task.getId());
    }

    @Test
    void runningCancellationClosesTheActiveAgentCall() {
        AsyncTask task = runningTask("RUNNING", 1, 3);
        when(repository.findByIdAndOwnerUserId(task.getId(), 7L)).thenReturn(Optional.of(task));

        AsyncTaskResponse response = service.cancelForUser(task.getId(), 7L);

        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(task.getCancelRequestedAt()).isNotNull();
        verify(agentHttpClient).cancelTask(task.getId());
        verify(planWorkflowStateService).markCancelled(task.getId());
    }

    @Test
    void runningPlanPausePreservesPayloadAndCheckpointForResume() {
        AsyncTask task = runningTask("RUNNING", 2, 3);
        when(repository.findByIdAndOwnerUserId(task.getId(), 7L)).thenReturn(Optional.of(task));

        AsyncTaskResponse response = service.pauseForUser(task.getId(), 7L);

        assertThat(response.status()).isEqualTo("PAUSED");
        assertThat(task.getRequestPayload()).isNotBlank();
        assertThat(task.getPauseRequestedAt()).isNotNull();
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getLeaseOwner()).isNull();
        verify(planWorkflowStateService).markPaused(task.getId());
        verify(agentHttpClient).cancelTask(task.getId());
    }

    @Test
    void pausedPlanResumeReturnsTaskToClaimablePendingState() {
        AsyncTask task = runningTask("PAUSED", 1, 3);
        task.setPauseRequestedAt(OffsetDateTime.now());
        when(repository.findByIdAndOwnerUserId(task.getId(), 7L)).thenReturn(Optional.of(task));

        AsyncTaskResponse response = service.resumeForUser(task.getId(), 7L);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(task.getPauseRequestedAt()).isNull();
        assertThat(task.getFinishedAt()).isNull();
        assertThat(task.getDeadlineAt()).isAfter(OffsetDateTime.now());
        verify(planWorkflowStateService).markResumed(task.getId());
    }

    @Test
    void workerFailureCannotTurnPausedTaskBackIntoPending() {
        AsyncTask task = runningTask("PAUSED", 1, 3);
        task.setPauseRequestedAt(OffsetDateTime.now());
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));

        service.failOrRetry(task.getId(), new IllegalStateException("cancelled call"));

        assertThat(task.getStatus()).isEqualTo("PAUSED");
        assertThat(task.getRequestPayload()).isNotBlank();
        assertThat(task.getErrorCode()).isNull();
    }

    @Test
    void lateCompletionCannotOverridePausedState() {
        AsyncTask task = runningTask("PAUSED", 1, 3);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));

        service.complete(task.getId(), 42L);

        assertThat(task.getStatus()).isEqualTo("PAUSED");
        assertThat(task.getResultResourceId()).isNull();
    }

    @Test
    void failureRetriesThenMovesToReplayableDeadLetter() {
        AsyncTask task = runningTask("RUNNING", 1, 2);
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));

        service.failOrRetry(task.getId(), new IllegalStateException("sensitive detail"));
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getErrorSummary()).doesNotContain("sensitive detail");

        task.setStatus("RUNNING");
        task.setAttemptCount(2);
        service.failOrRetry(task.getId(), new IllegalStateException("sensitive detail"));
        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getRequestPayload()).isNotBlank();

        AsyncTaskResponse replayed = service.replayFailed(task.getId());
        assertThat(replayed.status()).isEqualTo("PENDING");
        assertThat(replayed.attemptCount()).isZero();
    }

    @Test
    void taskLookupNeverFallsBackToAnUnownedIdentifier() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByIdAndOwnerUserId(taskId, 7L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.getForUser(taskId, 7L));

        verify(repository).findByIdAndOwnerUserId(taskId, 7L);
    }

    @Test
    void reusedIdempotencyKeyRejectsDifferentPayload() {
        AsyncTask existing = runningTask("PENDING", 0, 3);
        existing.setRequestFingerprint("different-request");
        when(repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(7L, "PLAN_GENERATION", "request-1"))
                .thenReturn(Optional.of(existing));

        assertThrows(
                ResponseStatusException.class,
                () -> service.createPlanTask(validGoal(), 7L, "request-1")
        );
    }

    @Test
    void resourceIngestionTaskStoresOnlyResourceReferencesAndDeduplicatesBySourceFingerprint() {
        UUID ingestionId = UUID.randomUUID();
        when(repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(7L, "RESOURCE_INGESTION", "resource-1"))
                .thenReturn(Optional.empty());

        AsyncTaskService.TaskSubmission submission = service.createResourceIngestionTask(
                ingestionId, 42L, 7L, "resource-1", "source-fingerprint"
        );

        assertThat(submission.created()).isTrue();
        assertThat(submission.ingestionId()).isEqualTo(ingestionId);
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(task ->
                task.getRequestPayload().contains(ingestionId.toString())
                        && task.getRequestPayload().contains("42")
                        && !task.getRequestPayload().contains("source-fingerprint")
        ));
    }

    @Test
    void resourceEmbeddingTaskStoresOnlyVersionedReferences() {
        UUID ingestionId = UUID.randomUUID();
        when(repository.findByOwnerUserIdAndTaskTypeAndIdempotencyKey(
                7L,
                "RESOURCE_EMBEDDING",
                "embedding:" + ingestionId + ":text-embedding-3-small-v1"
        )).thenReturn(Optional.empty());

        AsyncTaskResponse created = service.createResourceEmbeddingTask(
                ingestionId,
                42L,
                7L,
                "text-embedding-3-small-v1"
        );

        assertThat(created.taskType()).isEqualTo("RESOURCE_EMBEDDING");
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(task ->
                task.getRequestPayload().contains(ingestionId.toString())
                        && task.getRequestPayload().contains("42")
                        && task.getRequestPayload().contains("text-embedding-3-small-v1")
                        && !task.getRequestPayload().contains("content")
                        && !task.getRequestPayload().contains("text\"")
        ));
    }

    private static GoalRequest validGoal() {
        GoalRequest request = new GoalRequest();
        request.setGoalText("学习 Java");
        request.setDurationWeeks(8);
        request.setHoursPerDay(1);
        request.setLevel("beginner");
        return request;
    }

    private static AsyncTask runningTask(String status, int attempts, int maxAttempts) {
        OffsetDateTime now = OffsetDateTime.now();
        AsyncTask task = new AsyncTask();
        task.setId(UUID.randomUUID());
        task.setTaskType("PLAN_GENERATION");
        task.setOwnerUserId(7L);
        task.setIdempotencyKey("request-1");
        task.setRequestFingerprint("fingerprint");
        task.setStatus(status);
        task.setProgress(10);
        task.setRequestPayload("{\"goalText\":\"test\"}");
        task.setAttemptCount(attempts);
        task.setMaxAttempts(maxAttempts);
        task.setNextAttemptAt(now);
        task.setDeadlineAt(now.plusMinutes(10));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }
}
