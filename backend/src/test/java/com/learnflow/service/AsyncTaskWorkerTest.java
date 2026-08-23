package com.learnflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.config.LearnFlowTaskProperties;
import com.learnflow.dto.GoalRequest;
import com.learnflow.dto.PlanResponse;
import com.learnflow.entity.AsyncTask;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncTaskWorkerTest {

    @Test
    void executesPlanOutsideClaimTransactionAndCompletesWithResourceReference() throws Exception {
        LearnFlowTaskProperties properties = new LearnFlowTaskProperties();
        AsyncTaskLeaseService leases = mock(AsyncTaskLeaseService.class);
        AsyncTaskService tasks = mock(AsyncTaskService.class);
        AiProxyService ai = mock(AiProxyService.class);
        PlanPersistenceService persistence = mock(PlanPersistenceService.class);
        PlanWorkflowStateService workflow = mock(PlanWorkflowStateService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AsyncTask task = task(objectMapper);
        PlanResponse draft = new PlanResponse();
        when(tasks.load(task.getId())).thenReturn(task);
        when(tasks.isCancellationRequested(task.getId())).thenReturn(false);
        when(tasks.isDeadlineExceeded(task.getId())).thenReturn(false);
        when(ai.generatePlanDraft(any(GoalRequest.class), eq(task.getId()))).thenReturn(draft);
        when(persistence.persist(any(GoalRequest.class), eq(draft), eq(task.getId()))).thenReturn(42L);
        AsyncTaskWorker worker = new AsyncTaskWorker(
                properties,
                leases,
                tasks,
                ai,
                persistence,
                workflow,
                objectMapper
        );

        worker.execute(task.getId());

        verify(tasks).updateProgress(task.getId(), 10);
        verify(tasks).updateProgress(task.getId(), 70);
        verify(tasks).complete(task.getId(), 42L);
        verify(workflow).markSaved(task.getId(), 42L);
        verify(tasks, never()).failOrRetry(eq(task.getId()), any());
        worker.shutdown();
    }

    private static AsyncTask task(ObjectMapper objectMapper) throws Exception {
        GoalRequest request = new GoalRequest();
        request.setUserId(7L);
        request.setGoalText("Java");
        request.setDurationWeeks(8);
        request.setHoursPerDay(1);
        request.setLevel("beginner");
        AsyncTask task = new AsyncTask();
        task.setId(UUID.randomUUID());
        task.setTaskType(AsyncTaskService.PLAN_GENERATION);
        task.setStatus("RUNNING");
        task.setRequestPayload(objectMapper.writeValueAsString(request));
        task.setDeadlineAt(OffsetDateTime.now().plusMinutes(10));
        return task;
    }
}
