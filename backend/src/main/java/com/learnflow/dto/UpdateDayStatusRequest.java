package com.learnflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新某一天学习计划状态的请求体。
 *
 * 前端可以传入小写或大写的状态字符串：
 * - not_started / in_progress / completed / delayed
 * 后端会在持久化前统一转换为大写，方便统计。
 */
public class UpdateDayStatusRequest {

    @NotBlank
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}



