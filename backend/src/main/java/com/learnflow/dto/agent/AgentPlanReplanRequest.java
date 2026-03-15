package com.learnflow.dto.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.learnflow.dto.PlanResponse;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentPlanReplanRequest {

    private String goalText;
    private Integer durationWeeks;
    private Integer hoursPerDay;
    private String level;
    private String targetRole;
    private String preferredStyle;
    private List<String> constraints;
    private String finalDeliverable;
    private PlanResponse currentPlan;
    private Integer triggerDayIndex;
    private Integer delayDays;
    private String reason;

    public String getGoalText() {
        return goalText;
    }

    public void setGoalText(String goalText) {
        this.goalText = goalText;
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

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public String getPreferredStyle() {
        return preferredStyle;
    }

    public void setPreferredStyle(String preferredStyle) {
        this.preferredStyle = preferredStyle;
    }

    public List<String> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<String> constraints) {
        this.constraints = constraints;
    }

    public String getFinalDeliverable() {
        return finalDeliverable;
    }

    public void setFinalDeliverable(String finalDeliverable) {
        this.finalDeliverable = finalDeliverable;
    }

    public PlanResponse getCurrentPlan() {
        return currentPlan;
    }

    public void setCurrentPlan(PlanResponse currentPlan) {
        this.currentPlan = currentPlan;
    }

    public Integer getTriggerDayIndex() {
        return triggerDayIndex;
    }

    public void setTriggerDayIndex(Integer triggerDayIndex) {
        this.triggerDayIndex = triggerDayIndex;
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