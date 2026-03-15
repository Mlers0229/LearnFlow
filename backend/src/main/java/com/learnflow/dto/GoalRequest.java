package com.learnflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * ??????????????
 */
public class GoalRequest {

    @NotBlank(message = "????????")
    private String goalText;

    @NotNull(message = "???????????")
    @Min(value = 1, message = "??????? 1 ?")
    @Max(value = 52, message = "???????? 52 ?")
    private Integer durationWeeks;

    @NotNull(message = "??????????")
    @Min(value = 1, message = "?????? 1 ??")
    @Max(value = 10, message = "??????????? 10 ??")
    private Integer hoursPerDay;

    @NotBlank(message = "????????")
    private String level;

    private String targetRole;

    private String preferredStyle;

    private List<String> constraints;

    private String finalDeliverable;

    private Long userId;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
