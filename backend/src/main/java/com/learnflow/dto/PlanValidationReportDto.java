package com.learnflow.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlanValidationReportDto {

    private Boolean isValid;
    private List<PlanValidationIssueDto> issues;
    private List<PlanValidationIssueDto> warnings;
    private Integer coverageScore;
    private Integer repetitionScore;
    private Integer loadBalanceScore;
    private List<String> suggestedFixes;

    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean isValid) {
        this.isValid = isValid;
    }

    public List<PlanValidationIssueDto> getIssues() {
        return issues;
    }

    public void setIssues(List<PlanValidationIssueDto> issues) {
        this.issues = issues;
    }

    public List<PlanValidationIssueDto> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<PlanValidationIssueDto> warnings) {
        this.warnings = warnings;
    }

    public Integer getCoverageScore() {
        return coverageScore;
    }

    public void setCoverageScore(Integer coverageScore) {
        this.coverageScore = coverageScore;
    }

    public Integer getRepetitionScore() {
        return repetitionScore;
    }

    public void setRepetitionScore(Integer repetitionScore) {
        this.repetitionScore = repetitionScore;
    }

    public Integer getLoadBalanceScore() {
        return loadBalanceScore;
    }

    public void setLoadBalanceScore(Integer loadBalanceScore) {
        this.loadBalanceScore = loadBalanceScore;
    }

    public List<String> getSuggestedFixes() {
        return suggestedFixes;
    }

    public void setSuggestedFixes(List<String> suggestedFixes) {
        this.suggestedFixes = suggestedFixes;
    }
}
