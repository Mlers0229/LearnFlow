package com.learnflow.controller;

import com.learnflow.dto.AsyncTaskResponse;
import com.learnflow.dto.GoalRequest;
import com.learnflow.service.AsyncTaskService;
import com.learnflow.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AsyncTaskController {

    private final AsyncTaskService taskService;
    private final CurrentUserService currentUserService;

    public AsyncTaskController(AsyncTaskService taskService, CurrentUserService currentUserService) {
        this.taskService = taskService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/plan/tasks")
    public ResponseEntity<AsyncTaskResponse> createPlanTask(
            @Valid @RequestBody GoalRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        AsyncTaskResponse task = taskService.createPlanTask(
                request,
                currentUserService.requireUserId(),
                idempotencyKey
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/tasks/" + task.id()))
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(task);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<AsyncTaskResponse> getTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.getForUser(taskId, currentUserService.requireUserId()));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<AsyncTaskResponse> cancelTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.cancelForUser(taskId, currentUserService.requireUserId()));
    }

    @PostMapping("/tasks/{taskId}/pause")
    public ResponseEntity<AsyncTaskResponse> pauseTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.pauseForUser(taskId, currentUserService.requireUserId()));
    }

    @PostMapping("/tasks/{taskId}/resume")
    public ResponseEntity<AsyncTaskResponse> resumeTask(@PathVariable UUID taskId) {
        return ResponseEntity.accepted()
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(taskService.resumeForUser(taskId, currentUserService.requireUserId()));
    }
}
