package com.learnflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户上传学习资源的请求体。
 */
public class ResourceCreateRequest {

    private Long uploaderUserId;

    private String uploaderUsername;

    @NotBlank
    private String title;

    @NotBlank
    private String url;

    /**
     * 适用水平：beginner / intermediate / advanced，可选。
     */
    private String level;

    @NotBlank
    private String domain;

    private Integer durationMinutes;

    /**
     * 逗号分隔的标签字符串，例如 "java,basic,intro"。
     */
    private String tags;

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
}


