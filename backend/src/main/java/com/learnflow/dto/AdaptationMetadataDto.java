package com.learnflow.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** Bounded, privacy-minimized context produced by the deterministic adaptive policy. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AdaptationMetadataDto {

    private String policyVersion;
    private String variant;
    private Boolean applied;
    private String reason;
    private String targetDifficulty;
    private Integer reviewIntervalDays;
    private String reviewPriority;
    private String exerciseFocus;
    private List<AdaptiveKnowledgePointDto> weakPoints = List.of();

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public Boolean getApplied() { return applied; }
    public void setApplied(Boolean applied) { this.applied = applied; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getTargetDifficulty() { return targetDifficulty; }
    public void setTargetDifficulty(String targetDifficulty) { this.targetDifficulty = targetDifficulty; }
    public Integer getReviewIntervalDays() { return reviewIntervalDays; }
    public void setReviewIntervalDays(Integer reviewIntervalDays) { this.reviewIntervalDays = reviewIntervalDays; }
    public String getReviewPriority() { return reviewPriority; }
    public void setReviewPriority(String reviewPriority) { this.reviewPriority = reviewPriority; }
    public String getExerciseFocus() { return exerciseFocus; }
    public void setExerciseFocus(String exerciseFocus) { this.exerciseFocus = exerciseFocus; }
    public List<AdaptiveKnowledgePointDto> getWeakPoints() { return weakPoints; }
    public void setWeakPoints(List<AdaptiveKnowledgePointDto> weakPoints) {
        this.weakPoints = weakPoints == null ? List.of() : List.copyOf(weakPoints);
    }
}
