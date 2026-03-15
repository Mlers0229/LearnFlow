package com.learnflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PlanReplanRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long triggerDayId;

    @Min(1)
    private Integer delayDays;

    @Size(max = 500)
    private String reason;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTriggerDayId() {
        return triggerDayId;
    }

    public void setTriggerDayId(Long triggerDayId) {
        this.triggerDayId = triggerDayId;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    public void setDelayDays(Integer delayDays) {
        this.delayDays = delayDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
