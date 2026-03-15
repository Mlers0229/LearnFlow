package com.learnflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.dto.PlanDayDto;
import com.learnflow.dto.PlanReplanRequest;
import com.learnflow.dto.PlanResponse;
import com.learnflow.dto.agent.AgentPlanReplanRequest;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class PlanReplanAiService {

    private static final Logger log = LoggerFactory.getLogger(PlanReplanAiService.class);

    private final RestTemplate restTemplate;
    private final String agentBaseUrl;
    private final ObjectMapper objectMapper;

    public PlanReplanAiService(RestTemplate restTemplate,
                               @Value("${learnflow.ai-agent.base-url}") String agentBaseUrl,
                               ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.agentBaseUrl = agentBaseUrl;
        this.objectMapper = objectMapper;
    }

    public PlanResponse replan(StudyPlan plan,
                               List<StudyPlanDay> currentDays,
                               PlanReplanRequest request) {
        Optional<StudyPlanDay> triggerDayOpt = currentDays.stream()
                .filter(day -> day.getId() != null && day.getId().equals(request.getTriggerDayId()))
                .findFirst();
        if (triggerDayOpt.isEmpty()) {
            return null;
        }

        AgentPlanReplanRequest payload = new AgentPlanReplanRequest();
        payload.setGoalText(plan.getGoalText());
        payload.setDurationWeeks(plan.getDurationWeeks());
        payload.setHoursPerDay(plan.getHoursPerDay());
        payload.setLevel(plan.getLevel());
        payload.setCurrentPlan(buildCurrentPlanSnapshot(plan, currentDays));
        payload.setTriggerDayIndex(triggerDayOpt.get().getDayIndex());
        payload.setDelayDays(request.getDelayDays() != null && request.getDelayDays() > 0 ? request.getDelayDays() : 1);
        payload.setReason(request.getReason());

        try {
            String rawResponse = postJson(
                    agentBaseUrl + "/api/v2/plan/replan",
                    payload,
                    String.class
            );
            if (rawResponse == null || rawResponse.isBlank()) {
                return null;
            }
            return objectMapper.readValue(rawResponse, PlanResponse.class);
        } catch (JsonProcessingException e) {
            log.error("解析 AI 重规划响应失败，planId={}", plan.getId(), e);
            return null;
        } catch (RestClientException e) {
            log.error("调用 AI 重规划接口失败，planId={}", plan.getId(), e);
            return null;
        }
    }

    private PlanResponse buildCurrentPlanSnapshot(StudyPlan plan, List<StudyPlanDay> currentDays) {
        PlanResponse response = new PlanResponse();
        response.setPlanId(String.valueOf(plan.getId()));
        response.setTitle(plan.getTitle());
        response.setStartDate(plan.getStartDate());
        response.setEndDate(plan.getEndDate());
        response.setDays(currentDays.stream().map(this::mapDay).toList());
        return response;
    }

    private PlanDayDto mapDay(StudyPlanDay entity) {
        PlanDayDto dto = new PlanDayDto();
        dto.setId(entity.getId());
        dto.setDate(entity.getDate());
        dto.setTitle(entity.getTitle());
        dto.setTasks(readTasks(entity.getTasksJson()));
        dto.setDayIndex(entity.getDayIndex());
        dto.setWeekIndex(resolveWeekIndex(entity.getDayIndex()));
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().toLowerCase() : "not_started");
        return dto;
    }

    private List<String> readTasks(String tasksJson) {
        if (tasksJson == null || tasksJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(
                    tasksJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private Integer resolveWeekIndex(Integer dayIndex) {
        if (dayIndex == null || dayIndex <= 0) {
            return null;
        }
        return ((dayIndex - 1) / 7) + 1;
    }

    private <T> T postJson(String url, Object payload, Class<T> responseType)
            throws RestClientException, JsonProcessingException {
        String requestBody = objectMapper.writeValueAsString(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<T> response = restTemplate.postForEntity(url, entity, responseType);
        return response.getBody();
    }
}
