package com.learnflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "resource_chunk")
public class ResourceChunk {
    @Id private UUID id;
    @Column(name = "resource_id", nullable = false) private Long resourceId;
    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "char(64)") private String contentHash;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(length = 16) private String language;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
