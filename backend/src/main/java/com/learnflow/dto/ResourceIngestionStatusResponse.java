package com.learnflow.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResourceIngestionStatusResponse(
        UUID ingestionId, Long resourceId, String sourceType, String status,
        String contentType, Long contentLength, String contentSha256, String language,
        String parserVersion, String chunkerVersion, int chunkCount, String errorCode,
        OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime finishedAt
) {}
