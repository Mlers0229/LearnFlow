package com.learnflow.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习计划列表页使用的简要信息 DTO。
 *
 * 用于 /api/plan/recent 接口，只暴露列表展示所需字段，
 * 详细的每日任务仍通过 /api/plan/{id} 获取。
 */
public class PlanSummaryDto {

    /**
     * 计划 ID（对应 study_plan.id）。
     */
    private Long id;

    /**
     * 计划标题。
     */
    private String title;

    /**
     * 计划开始日期。
     */
    private LocalDate startDate;

    /**
     * 计划结束日期。
     */
    private LocalDate endDate;

    /**
     * 计划状态：active / completed / cancelled。
     */
    private String status;

    /**
     * 计划创建时间，用于按时间倒序展示最近计划。
     */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


