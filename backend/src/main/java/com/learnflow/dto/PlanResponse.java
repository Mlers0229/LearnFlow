package com.learnflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * ?????????????
 */
public class PlanResponse {

    @JsonProperty("plan_id")
    private String planId;

    private String title;

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    private List<PlanDayDto> days;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("goal_blueprint")
    private GoalBlueprintDto goalBlueprint;

    private List<LearningPhaseDto> phases;

    private List<WeeklyPlanDto> weeks;

    @JsonProperty("validation_report")
    private PlanValidationReportDto validationReport;

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
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

    public List<PlanDayDto> getDays() {
        return days;
    }

    public void setDays(List<PlanDayDto> days) {
        this.days = days;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public GoalBlueprintDto getGoalBlueprint() {
        return goalBlueprint;
    }

    public void setGoalBlueprint(GoalBlueprintDto goalBlueprint) {
        this.goalBlueprint = goalBlueprint;
    }

    public List<LearningPhaseDto> getPhases() {
        return phases;
    }

    public void setPhases(List<LearningPhaseDto> phases) {
        this.phases = phases;
    }

    public List<WeeklyPlanDto> getWeeks() {
        return weeks;
    }

    public void setWeeks(List<WeeklyPlanDto> weeks) {
        this.weeks = weeks;
    }

    public PlanValidationReportDto getValidationReport() {
        return validationReport;
    }

    public void setValidationReport(PlanValidationReportDto validationReport) {
        this.validationReport = validationReport;
    }
}
