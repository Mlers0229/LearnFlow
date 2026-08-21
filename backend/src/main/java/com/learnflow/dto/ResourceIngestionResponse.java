package com.learnflow.dto;

import java.util.UUID;

public record ResourceIngestionResponse(Long resourceId, UUID ingestionId, AsyncTaskResponse task) {}
