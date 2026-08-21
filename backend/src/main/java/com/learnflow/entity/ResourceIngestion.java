package com.learnflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "resource_ingestion")
public class ResourceIngestion {
    @Id private UUID id;
    @Column(name = "resource_id", nullable = false) private Long resourceId;
    @Column(name = "source_type", nullable = false, length = 16) private String sourceType;
    @Column(name = "source_locator", columnDefinition = "text") private String sourceLocator;
    @Column(name = "object_key", length = 512) private String objectKey;
    @Column(name = "original_filename", length = 255) private String originalFilename;
    @Column(name = "content_type", length = 128) private String contentType;
    @Column(name = "content_length") private Long contentLength;
    @Column(name = "content_sha256", length = 64) private String contentSha256;
    @Column(length = 16) private String language;
    @Column(name = "parser_version", nullable = false, length = 64) private String parserVersion;
    @Column(name = "chunker_version", nullable = false, length = 64) private String chunkerVersion;
    @Column(name = "rights_confirmed_at", nullable = false) private OffsetDateTime rightsConfirmedAt;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "started_at") private OffsetDateTime startedAt;
    @Column(name = "finished_at") private OffsetDateTime finishedAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getContentLength() { return contentLength; }
    public void setContentLength(Long contentLength) { this.contentLength = contentLength; }
    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public String getChunkerVersion() { return chunkerVersion; }
    public void setChunkerVersion(String chunkerVersion) { this.chunkerVersion = chunkerVersion; }
    public OffsetDateTime getRightsConfirmedAt() { return rightsConfirmedAt; }
    public void setRightsConfirmedAt(OffsetDateTime rightsConfirmedAt) { this.rightsConfirmedAt = rightsConfirmedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
