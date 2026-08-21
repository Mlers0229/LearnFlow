package com.learnflow.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AsyncTaskResponse(
        UUID id,
        String taskType,
        String status,
        int progress,
        int attemptCount,
        int maxAttempts,
        String resultResourceType,
        Long resultResourceId,
        String errorCode,
        String errorSummary,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime updatedAt
) {
}
