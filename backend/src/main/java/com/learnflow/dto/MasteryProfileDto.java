package com.learnflow.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class MasteryProfileDto {

    private Long knowledgePointId;
    private String knowledgeKey;
    private String displayName;
    private Double masteryScore;
    private Double confidence;
    private Double effectiveWeight;
    private Integer sampleCount;
    private String algorithmVersion;
    private OffsetDateTime calculatedAt;
    private List<MasteryEvidenceDto> evidence = List.of();

    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
    public String getKnowledgeKey() { return knowledgeKey; }
    public void setKnowledgeKey(String knowledgeKey) { this.knowledgeKey = knowledgeKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Double getMasteryScore() { return masteryScore; }
    public void setMasteryScore(Double masteryScore) { this.masteryScore = masteryScore; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Double getEffectiveWeight() { return effectiveWeight; }
    public void setEffectiveWeight(Double effectiveWeight) { this.effectiveWeight = effectiveWeight; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public void setAlgorithmVersion(String algorithmVersion) { this.algorithmVersion = algorithmVersion; }
    public OffsetDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(OffsetDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
    public List<MasteryEvidenceDto> getEvidence() { return evidence; }
    public void setEvidence(List<MasteryEvidenceDto> evidence) { this.evidence = evidence == null ? List.of() : List.copyOf(evidence); }
}

