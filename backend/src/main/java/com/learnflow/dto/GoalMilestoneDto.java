package com.learnflow.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GoalMilestoneDto {

    private String title;
    private String description;
    private Integer suggestedWeek;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSuggestedWeek() {
        return suggestedWeek;
    }

    public void setSuggestedWeek(Integer suggestedWeek) {
        this.suggestedWeek = suggestedWeek;
    }
}
