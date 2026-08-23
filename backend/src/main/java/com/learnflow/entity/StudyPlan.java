package com.learnflow.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习计划总表，对应一整份学习计划。
 *
 * 目前先不接入真实用户体系，因此 userId 先保留为可空字段。
 */
@Entity
@Table(name = "study_plan")
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 预留用户 ID，后续接入登录系统后使用。
     */
    private Long userId;

    /**
     * 用户原始输入的学习目标描述。
     */
    @Column(columnDefinition = "text")
    private String goalText;

    /**
     * 计划标题，例如“Java 入门 8 周计划”。
     */
    private String title;

    private Integer durationWeeks;

    private Integer hoursPerDay;

    /**
     * 学习者基础水平：beginner / intermediate / advanced。
     */
    private String level;

    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * 计划状态：active / completed / cancelled。
     */
    private String status;

    @Column(name = "source_task_id", unique = true)
    private UUID sourceTaskId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String adaptationPolicyVersion;
    private String adaptationVariant;
    private Boolean adaptationApplied;
    private String adaptationReason;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = "active";
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGoalText() {
        return goalText;
    }

    public void setGoalText(String goalText) {
        this.goalText = goalText;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getDurationWeeks() {
        return durationWeeks;
    }

    public void setDurationWeeks(Integer durationWeeks) {
        this.durationWeeks = durationWeeks;
    }

    public Integer getHoursPerDay() {
        return hoursPerDay;
    }

    public void setHoursPerDay(Integer hoursPerDay) {
        this.hoursPerDay = hoursPerDay;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getSourceTaskId() {
        return sourceTaskId;
    }

    public void setSourceTaskId(UUID sourceTaskId) {
        this.sourceTaskId = sourceTaskId;
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

    public String getAdaptationPolicyVersion() { return adaptationPolicyVersion; }
    public void setAdaptationPolicyVersion(String adaptationPolicyVersion) { this.adaptationPolicyVersion = adaptationPolicyVersion; }
    public String getAdaptationVariant() { return adaptationVariant; }
    public void setAdaptationVariant(String adaptationVariant) { this.adaptationVariant = adaptationVariant; }
    public Boolean getAdaptationApplied() { return adaptationApplied; }
    public void setAdaptationApplied(Boolean adaptationApplied) { this.adaptationApplied = adaptationApplied; }
    public String getAdaptationReason() { return adaptationReason; }
    public void setAdaptationReason(String adaptationReason) { this.adaptationReason = adaptationReason; }
}


