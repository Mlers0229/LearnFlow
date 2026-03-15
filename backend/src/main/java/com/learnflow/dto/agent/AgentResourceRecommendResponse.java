package com.learnflow.dto.agent;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentResourceRecommendResponse {

    private List<AgentResourceItem> resources = new ArrayList<>();
    private List<String> expandedQueries = new ArrayList<>();
    private String rerankStrategy;
    private String querySummary;

    public List<AgentResourceItem> getResources() {
        return resources;
    }

    public void setResources(List<AgentResourceItem> resources) {
        this.resources = resources;
    }

    public List<String> getExpandedQueries() {
        return expandedQueries;
    }

    public void setExpandedQueries(List<String> expandedQueries) {
        this.expandedQueries = expandedQueries;
    }

    public String getRerankStrategy() {
        return rerankStrategy;
    }

    public void setRerankStrategy(String rerankStrategy) {
        this.rerankStrategy = rerankStrategy;
    }

    public String getQuerySummary() {
        return querySummary;
    }

    public void setQuerySummary(String querySummary) {
        this.querySummary = querySummary;
    }
}
