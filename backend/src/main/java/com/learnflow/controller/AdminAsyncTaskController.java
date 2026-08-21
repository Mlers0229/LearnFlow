package com.learnflow.controller;

import com.learnflow.dto.AsyncTaskResponse;
import com.learnflow.service.AsyncTaskService;
import com.learnflow.service.AdminAuditLogService;
import com.learnflow.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tasks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAsyncTaskController {

    private final AsyncTaskService taskService;
    private final AdminAuditLogService auditLogService;
    private final CurrentUserService currentUserService;

    public AdminAsyncTaskController(
            AsyncTaskService taskService,
            AdminAuditLogService auditLogService,
            CurrentUserService currentUserService
    ) {
        this.taskService = taskService;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/failed")
    public ResponseEntity<List<AsyncTaskResponse>> failedTasks() {
        return ResponseEntity.ok(taskService.failedTasks());
    }

    @PostMapping("/{taskId}/replay")
    public ResponseEntity<AsyncTaskResponse> replay(@PathVariable UUID taskId) {
        AsyncTaskResponse replayed = taskService.replayFailed(taskId);
        auditLogService.record(
                "ASYNC_TASK_REPLAY",
                currentUserService.requireUsername(),
                "ASYNC_TASK",
                null,
                "taskId=" + taskId
        );
        return ResponseEntity.accepted().body(replayed);
    }
}
