package com.learnflow.dto;

import java.time.LocalDateTime;

/**
 * 学习资源条目 DTO，用于从后端返回给前端展示。
 *
 * 字段与 FastAPI 侧的 ResourceItem 基本对应。
 */
public class ResourceItemDto {

    /**
     * 资源 ID（对应 resource_bank.id）。可用于前端管理和删除。
     */
    private Long id;

    private Long uploaderUserId;

    private String uploaderUsername;

    private String title;

    private String url;

    private String level;

    private String domain;

    private Integer durationMinutes;

    private String tags; // 简单起见先用逗号分隔的字符串

    /**
     * 资源状态：PENDING / ACTIVE / INACTIVE。
     * 仅在管理端资源列表中使用，普通用户一般只看到 ACTIVE 状态资源。
     */
    private String status;

    private String reason;

    private Double score;

    private String matchedTerms;

    private String source;

    private String querySummary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Double avgRating;

    private Long feedbackCount;

    private Long invalidReportCount;

    private Integer currentUserRating;

    private Boolean currentUserReportedInvalid;

    private String currentUserFeedback;

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

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getMatchedTerms() {
        return matchedTerms;
    }

    public void setMatchedTerms(String matchedTerms) {
        this.matchedTerms = matchedTerms;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getQuerySummary() {
        return querySummary;
    }

    public void setQuerySummary(String querySummary) {
        this.querySummary = querySummary;
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

    public Double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(Double avgRating) {
        this.avgRating = avgRating;
    }

    public Long getFeedbackCount() {
        return feedbackCount;
    }

    public void setFeedbackCount(Long feedbackCount) {
        this.feedbackCount = feedbackCount;
    }

    public Long getInvalidReportCount() {
        return invalidReportCount;
    }

    public void setInvalidReportCount(Long invalidReportCount) {
        this.invalidReportCount = invalidReportCount;
    }

    public Integer getCurrentUserRating() {
        return currentUserRating;
    }

    public void setCurrentUserRating(Integer currentUserRating) {
        this.currentUserRating = currentUserRating;
    }

    public Boolean getCurrentUserReportedInvalid() {
        return currentUserReportedInvalid;
    }

    public void setCurrentUserReportedInvalid(Boolean currentUserReportedInvalid) {
        this.currentUserReportedInvalid = currentUserReportedInvalid;
    }

    public String getCurrentUserFeedback() {
        return currentUserFeedback;
    }

    public void setCurrentUserFeedback(String currentUserFeedback) {
        this.currentUserFeedback = currentUserFeedback;
    }
}
