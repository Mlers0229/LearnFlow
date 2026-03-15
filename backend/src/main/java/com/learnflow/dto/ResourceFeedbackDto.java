package com.learnflow.dto;

import java.time.LocalDateTime;

public class ResourceFeedbackDto {
    private Long id;
    private Integer rating;
    private String comment;
    private Boolean reportedInvalid;
    private LocalDateTime createdAt;
    private Long userId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Boolean getReportedInvalid() {
        return reportedInvalid;
    }

    public void setReportedInvalid(Boolean reportedInvalid) {
        this.reportedInvalid = reportedInvalid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}


