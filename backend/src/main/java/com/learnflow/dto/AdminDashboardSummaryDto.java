package com.learnflow.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminDashboardSummaryDto {

    private List<ResourceItemDto> resources = new ArrayList<>();
    private List<ResourceQualityStatsDto> resourceQualityStats = new ArrayList<>();
    private List<UserDto> users = new ArrayList<>();
    private List<AgentCallLogDto> agentLogs = new ArrayList<>();
    private List<PlanSummaryDto> recentPlans = new ArrayList<>();
    private List<FeedbackTrendPoint> feedbackTrend = new ArrayList<>();
    private JsonNode modelConfig;
    private long totalPlanCount;
    private long recentPlanCount7d;
    private Map<String, Long> taskStatusCounts = new LinkedHashMap<>();

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

    public long getTotalPlanCount() {
        return totalPlanCount;
    }

    public void setTotalPlanCount(long totalPlanCount) {
        this.totalPlanCount = totalPlanCount;
    }

    public long getRecentPlanCount7d() {
        return recentPlanCount7d;
    }

    public void setRecentPlanCount7d(long recentPlanCount7d) {
        this.recentPlanCount7d = recentPlanCount7d;
    }

    public Map<String, Long> getTaskStatusCounts() {
        return taskStatusCounts;
    }

    public void setTaskStatusCounts(Map<String, Long> taskStatusCounts) {
        this.taskStatusCounts = taskStatusCounts;
    }
}
