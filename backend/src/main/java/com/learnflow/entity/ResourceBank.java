package com.learnflow.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习资源库表，对应 Postgres 中的 resource_bank。
 *
 * 资源与学习计划解耦，用户可以自主上传资源到这个表，
 * RAG Agent 和前端推荐展示都从这里读取数据。
 */
@Entity
@Table(name = "resource_bank")
public class ResourceBank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_user_id")
    private Long uploaderUserId;

    @Column(name = "uploader_username", length = 100)
    private String uploaderUsername;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String url;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType = "URL";

    @Column(name = "ingestion_status", nullable = false, length = 16)
    private String ingestionStatus = "NOT_STARTED";

    @Column(name = "current_ingestion_id")
    private UUID currentIngestionId;

    /**
     * 适用水平：beginner / intermediate / advanced。
     */
    private String level;

    /**
     * 资源所属领域，例如 java / english / math / database。
     */
    @Column(length = 50)
    private String domain;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /**
     * 使用逗号分隔的标签字符串，例如 "java,basic,intro"。
     */
    @Column(columnDefinition = "text")
    private String tags;

    /**
     * 资源状态：PENDING / ACTIVE / INACTIVE。
     * - PENDING：用户提交，待管理端审核；
     * - ACTIVE：已审核通过，可用于推荐；
     * - INACTIVE：已下线或拒绝。
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUploaderUserId() {
        return uploaderUserId;
    }

    public void setUploaderUserId(Long uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    public String getUploaderUsername() {
        return uploaderUsername;
    }

    public void setUploaderUsername(String uploaderUsername) {
        this.uploaderUsername = uploaderUsername;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getIngestionStatus() { return ingestionStatus; }
    public void setIngestionStatus(String ingestionStatus) { this.ingestionStatus = ingestionStatus; }
    public UUID getCurrentIngestionId() { return currentIngestionId; }
    public void setCurrentIngestionId(UUID currentIngestionId) { this.currentIngestionId = currentIngestionId; }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


