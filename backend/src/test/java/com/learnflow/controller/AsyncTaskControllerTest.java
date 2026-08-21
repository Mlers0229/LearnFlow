package com.learnflow.controller;

import com.learnflow.dto.AsyncTaskResponse;
import com.learnflow.dto.GoalRequest;
import com.learnflow.service.AdminAuditLogService;
import com.learnflow.service.AsyncTaskService;
import com.learnflow.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncTaskControllerTest {

    @Test
    void planSubmissionReturnsAcceptedLocationAndUsesJwtIdentity() {
        AsyncTaskService tasks = mock(AsyncTaskService.class);
        CurrentUserService users = mock(CurrentUserService.class);
        AsyncTaskController controller = new AsyncTaskController(tasks, users);
        UUID taskId = UUID.randomUUID();
        GoalRequest request = new GoalRequest();
        request.setUserId(999L);
        when(users.requireUserId()).thenReturn(7L);
        when(tasks.createPlanTask(request, 7L, "request-1")).thenReturn(task(taskId, "PENDING"));

        var response = controller.createPlanTask(request, "request-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/tasks/" + taskId);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("2");
        verify(tasks).createPlanTask(request, 7L, "request-1");
    }

    @Test
    void adminReplayIsAuditedWithoutTaskPayload() {
        AsyncTaskService tasks = mock(AsyncTaskService.class);
        AdminAuditLogService audit = mock(AdminAuditLogService.class);
        CurrentUserService users = mock(CurrentUserService.class);
        AdminAsyncTaskController controller = new AdminAsyncTaskController(tasks, audit, users);
        UUID taskId = UUID.randomUUID();
        when(tasks.replayFailed(taskId)).thenReturn(task(taskId, "PENDING"));
        when(users.requireUsername()).thenReturn("admin");

        var response = controller.replay(taskId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(audit).record("ASYNC_TASK_REPLAY", "admin", "ASYNC_TASK", null, "taskId=" + taskId);
    }

    private static AsyncTaskResponse task(UUID taskId, String status) {
        return new AsyncTaskResponse(
                taskId,
                "PLAN_GENERATION",
                status,
                0,
                0,
                3,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
