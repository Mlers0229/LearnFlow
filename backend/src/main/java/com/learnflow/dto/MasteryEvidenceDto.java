package com.learnflow.dto;

import java.time.OffsetDateTime;

public class MasteryEvidenceDto {

    private Long eventId;
    private String eventType;
    private String sourceType;
    private Long sourceId;
    private Double signalValue;
    private Double signalWeight;
    private String summary;
    private OffsetDateTime occurredAt;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Double getSignalValue() { return signalValue; }
    public void setSignalValue(Double signalValue) { this.signalValue = signalValue; }
    public Double getSignalWeight() { return signalWeight; }
    public void setSignalWeight(Double signalWeight) { this.signalWeight = signalWeight; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}

