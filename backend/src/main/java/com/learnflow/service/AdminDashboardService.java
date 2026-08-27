package com.learnflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnflow.dto.AdminDashboardSummaryDto;
import com.learnflow.dto.AgentCallLogDto;
import com.learnflow.dto.PlanSummaryDto;
import com.learnflow.entity.StudyPlan;
import com.learnflow.repository.StudyPlanRepository;
import com.learnflow.repository.AsyncTaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class AdminDashboardService {

    private final ResourceService resourceService;
    private final UserAdminService userAdminService;
    private final AiProxyService aiProxyService;
    private final StudyPlanRepository studyPlanRepository;
    private final ChatProxyService chatProxyService;
    private final AsyncTaskRepository asyncTaskRepository;

    public AdminDashboardService(ResourceService resourceService,
                                 UserAdminService userAdminService,
                                 AiProxyService aiProxyService,
                                 StudyPlanRepository studyPlanRepository,
                                 ChatProxyService chatProxyService,
                                 AsyncTaskRepository asyncTaskRepository) {
        this.resourceService = resourceService;
        this.userAdminService = userAdminService;
        this.aiProxyService = aiProxyService;
        this.studyPlanRepository = studyPlanRepository;
        this.chatProxyService = chatProxyService;
        this.asyncTaskRepository = asyncTaskRepository;
    }

    public AdminDashboardSummaryDto getDashboardSummary(int logLimit, int planLimit, int trendDays) {
        AdminDashboardSummaryDto dto = new AdminDashboardSummaryDto();
        dto.setResources(resourceService.listAllResources());
        dto.setResourceQualityStats(resourceService.aggregateQualityStats());
        dto.setUsers(userAdminService.listUsers());
        dto.setAgentLogs(getDashboardLogs(logLimit));
        dto.setRecentPlans(getRecentPlansAcrossUsers(planLimit));
        dto.setFeedbackTrend(resourceService.dailyTrend(trendDays));
        dto.setModelConfig(loadModelConfigSafely());
        dto.setTotalPlanCount(studyPlanRepository.count());
        dto.setRecentPlanCount7d(studyPlanRepository.countByCreatedAtAfter(LocalDateTime.now().minusDays(7)));
        dto.setTaskStatusCounts(loadTaskStatusCounts());
        return dto;
    }

    private Map<String, Long> loadTaskStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of("PENDING", "RUNNING", "PAUSED", "SUCCEEDED", "FAILED", "CANCELLED")) {
            counts.put(status, asyncTaskRepository.countByStatus(status));
        }
        return counts;
    }

    private List<PlanSummaryDto> getRecentPlansAcrossUsers(int limit) {
        return studyPlanRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .filter(plan -> plan.getStatus() == null || !"cancelled".equalsIgnoreCase(plan.getStatus()))
                .map(this::mapToPlanSummary)
                .toList();
    }

    private PlanSummaryDto mapToPlanSummary(StudyPlan plan) {
        PlanSummaryDto dto = new PlanSummaryDto();
        dto.setId(plan.getId());
        dto.setTitle(plan.getTitle());
        dto.setStartDate(plan.getStartDate());
        dto.setEndDate(plan.getEndDate());
        dto.setStatus(plan.getStatus());
        dto.setCreatedAt(plan.getCreatedAt());
        return dto;
    }

    private JsonNode loadModelConfigSafely() {
        try {
            return chatProxyService.fetchAdminChatConfig(false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<AgentCallLogDto> getDashboardLogs(int logLimit) {
        return aiProxyService.getAgentLogs(null, logLimit).stream()
                .map(this::toDashboardLog)
                .toList();
    }

    private AgentCallLogDto toDashboardLog(AgentCallLogDto source) {
        AgentCallLogDto dto = new AgentCallLogDto();
        dto.setId(source.getId());
        dto.setTraceId(source.getTraceId());
        dto.setAgentName(source.getAgentName());
        dto.setModelName(source.getModelName());
        dto.setDurationMs(source.getDurationMs());
        dto.setCreatedAt(source.getCreatedAt());
        dto.setRequestPayload(trimPayload(source.getRequestPayload()));
        dto.setResponsePayload(trimPayload(source.getResponsePayload()));
        return dto;
    }

    private String trimPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        if (payload.length() <= 240) {
            return payload;
        }
        return payload.substring(0, 240) + "...";
    }
}
