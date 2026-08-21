package com.learnflow.config;

import com.learnflow.service.FileSystemResourceSourceStore;
import com.learnflow.service.ResourceSourceStore;
import com.learnflow.service.S3ResourceSourceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class ResourceStorageConfig {
    @Bean
    ResourceSourceStore resourceSourceStore(ResourceIngestionProperties properties) {
        ResourceIngestionProperties.Storage storage = properties.getStorage();
        if ("filesystem".equalsIgnoreCase(storage.getType())) {
            return new FileSystemResourceSourceStore(storage.getFilesystemRoot());
        }
        if (!"s3".equalsIgnoreCase(storage.getType()) || storage.getBucket() == null || storage.getBucket().isBlank()) {
            throw new IllegalStateException("Resource source storage must be filesystem or a configured S3 bucket");
        }
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(storage.getRegion()))
                .forcePathStyle(storage.getEndpoint() != null);
        if (storage.getEndpoint() != null) builder.endpointOverride(storage.getEndpoint());
        return new S3ResourceSourceStore(builder.build(), storage.getBucket());
    }
}
