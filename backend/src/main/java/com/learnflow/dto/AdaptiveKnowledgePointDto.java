package com.learnflow.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AdaptiveKnowledgePointDto {

    private String knowledgeKey;
    private String displayName;
    private Double masteryScore;
    private Double confidence;
    private String masteryBand;

    public String getKnowledgeKey() { return knowledgeKey; }
    public void setKnowledgeKey(String knowledgeKey) { this.knowledgeKey = knowledgeKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Double getMasteryScore() { return masteryScore; }
    public void setMasteryScore(Double masteryScore) { this.masteryScore = masteryScore; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getMasteryBand() { return masteryBand; }
    public void setMasteryBand(String masteryBand) { this.masteryBand = masteryBand; }
}
