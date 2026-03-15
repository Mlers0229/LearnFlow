package com.learnflow.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class AdminDashboardSummaryDto {

    private List<ResourceItemDto> resources = new ArrayList<>();
    private List<ResourceQualityStatsDto> resourceQualityStats = new ArrayList<>();
    private List<UserDto> users = new ArrayList<>();
    private List<AgentCallLogDto> agentLogs = new ArrayList<>();
    private List<PlanSummaryDto> recentPlans = new ArrayList<>();
    private List<FeedbackTrendPoint> feedbackTrend = new ArrayList<>();
    private JsonNode modelConfig;

    public List<ResourceItemDto> getResources() {
        return resources;
    }

    public void setResources(List<ResourceItemDto> resources) {
        this.resources = resources;
    }

    public List<ResourceQualityStatsDto> getResourceQualityStats() {
        return resourceQualityStats;
    }

    public void setResourceQualityStats(List<ResourceQualityStatsDto> resourceQualityStats) {
        this.resourceQualityStats = resourceQualityStats;
    }

    public List<UserDto> getUsers() {
        return users;
    }

    public void setUsers(List<UserDto> users) {
        this.users = users;
    }

    public List<AgentCallLogDto> getAgentLogs() {
        return agentLogs;
    }

    public void setAgentLogs(List<AgentCallLogDto> agentLogs) {
        this.agentLogs = agentLogs;
    }

    public List<PlanSummaryDto> getRecentPlans() {
        return recentPlans;
    }

    public void setRecentPlans(List<PlanSummaryDto> recentPlans) {
        this.recentPlans = recentPlans;
    }

    public List<FeedbackTrendPoint> getFeedbackTrend() {
        return feedbackTrend;
    }

    public void setFeedbackTrend(List<FeedbackTrendPoint> feedbackTrend) {
        this.feedbackTrend = feedbackTrend;
    }

    public JsonNode getModelConfig() {
        return modelConfig;
    }

    public void setModelConfig(JsonNode modelConfig) {
        this.modelConfig = modelConfig;
    }
}
