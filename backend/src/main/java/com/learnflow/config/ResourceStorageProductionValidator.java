package com.learnflow.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ResourceStorageProductionValidator {
    private final String environment;
    private final ResourceIngestionProperties properties;

    public ResourceStorageProductionValidator(@Value("${learnflow.auth.environment:development}") String environment,
                                              ResourceIngestionProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (!properties.isEnabled() || !"production".equalsIgnoreCase(environment)) return;
        ResourceIngestionProperties.Storage storage = properties.getStorage();
        if (!"s3".equalsIgnoreCase(storage.getType()) || storage.getBucket() == null || storage.getBucket().isBlank()) {
            throw new IllegalStateException("Production resource ingestion requires configured S3-compatible object storage");
        }
    }
}
