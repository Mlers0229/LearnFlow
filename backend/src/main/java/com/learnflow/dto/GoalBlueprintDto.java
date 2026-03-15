package com.learnflow.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GoalBlueprintDto {

    private String summary;
    private String targetRole;
    private List<GoalTopicDto> topics;
    private List<GoalMilestoneDto> milestones;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public List<GoalTopicDto> getTopics() {
        return topics;
    }

    public void setTopics(List<GoalTopicDto> topics) {
        this.topics = topics;
    }

    public List<GoalMilestoneDto> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<GoalMilestoneDto> milestones) {
        this.milestones = milestones;
    }
}
