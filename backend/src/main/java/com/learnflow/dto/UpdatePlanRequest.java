package com.learnflow.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新学习计划的请求体。
 *
 * 当前仅支持：
 * - 修改计划标题 title；
 * - 更新计划状态 status（active / completed / cancelled）。
 */
public class UpdatePlanRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 20)
    private String status;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}



