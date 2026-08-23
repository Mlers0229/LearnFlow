package com.learnflow.dto.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.learnflow.dto.AdaptationMetadataDto;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentResourceQueryRequest {

    private String topic;
    private String level;
    private String domain;
    private String goalText;
    private List<String> taskTexts;
    private Integer estimatedMinutes;
    private String phaseTitle;
    private String weekTheme;
    private String taskType;
    private Integer topK;
    private AdaptationMetadataDto adaptiveContext;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getGoalText() {
        return goalText;
    }

    public void setGoalText(String goalText) {
        this.goalText = goalText;
    }

    public List<String> getTaskTexts() {
        return taskTexts;
    }

    public void setTaskTexts(List<String> taskTexts) {
        this.taskTexts = taskTexts;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public String getPhaseTitle() {
        return phaseTitle;
    }

    public void setPhaseTitle(String phaseTitle) {
        this.phaseTitle = phaseTitle;
    }

    public String getWeekTheme() {
        return weekTheme;
    }

    public void setWeekTheme(String weekTheme) {
        this.weekTheme = weekTheme;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public AdaptationMetadataDto getAdaptiveContext() { return adaptiveContext; }
    public void setAdaptiveContext(AdaptationMetadataDto adaptiveContext) { this.adaptiveContext = adaptiveContext; }
}
