package com.learnflow.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 用户对资源的评分 / 举报反馈表（user_resource_feedback）。
 *
 * 这一层数据用于后续：
 * - 统计某个资源整体评分；
 * - 支撑 Qscore 计算；
 * - 识别被频繁举报为“无效 / 不相关”的资源。
 */
@Entity
@Table(name = "user_resource_feedback")
public class UserResourceFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 哪个用户给出的反馈（可为空，兼容未登录场景）。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * 针对哪一条资源（resource_bank）。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_bank_id", nullable = false)
    private ResourceBank resource;

    /**
     * 用户对资源的评分（1~5），可空。
     */
    private Integer rating;

    /**
     * 简短文字评论，可空。
     */
    @Column(columnDefinition = "text")
    private String comment;

    /**
     * 是否举报该资源为“无效 / 不相关”。
     */
    @Column(name = "is_reported_invalid")
    private Boolean reportedInvalid;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ResourceBank getResource() {
        return resource;
    }

    public void setResource(ResourceBank resource) {
        this.resource = resource;
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
}



