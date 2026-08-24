package com.learnflow.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrivacyRequestResponse(
        UUID id,
        String type,
        String status,
        boolean downloadReady,
        OffsetDateTime artifactExpiresAt,
        String errorCode,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {}
