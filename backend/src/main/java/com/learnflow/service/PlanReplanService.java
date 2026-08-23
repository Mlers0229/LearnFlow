package com.learnflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.dto.AdaptationMetadataDto;
import com.learnflow.dto.PlanDayDto;
import com.learnflow.dto.PlanReplanRequest;
import com.learnflow.dto.PlanResponse;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.repository.StudyPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PlanReplanService {

    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanDayRepository studyPlanDayRepository;
    private final PlanQueryService planQueryService;
    private final PlanReplanAiService planReplanAiService;
    private final ObjectMapper objectMapper;
    private final AdaptiveLearningService adaptiveLearningService;

    public PlanReplanService(StudyPlanRepository studyPlanRepository,
                             StudyPlanDayRepository studyPlanDayRepository,
                             PlanQueryService planQueryService,
                             PlanReplanAiService planReplanAiService,
                             ObjectMapper objectMapper,
                             AdaptiveLearningService adaptiveLearningService) {
        this.studyPlanRepository = studyPlanRepository;
        this.studyPlanDayRepository = studyPlanDayRepository;
        this.planQueryService = planQueryService;
        this.planReplanAiService = planReplanAiService;
        this.objectMapper = objectMapper;
        this.adaptiveLearningService = adaptiveLearningService;
    }

    @Transactional
    public PlanResponse replan(Long planId, PlanReplanRequest request) {
        if (request.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId 不能为空");
        }

        StudyPlan plan = planQueryService.getPlanEntityByIdAndUser(planId, request.getUserId());
        List<StudyPlanDay> days = studyPlanDayRepository.findByPlan_IdOrderByDayIndexAsc(planId);
        if (days.isEmpty()) {
            return planQueryService.getPlanById(planId, request.getUserId());
        }

        StudyPlanDay triggerDay = days.stream()
                .filter(day -> Objects.equals(day.getId(), request.getTriggerDayId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "触发重规划的 dayId 不存在"));

        if ("COMPLETED".equalsIgnoreCase(triggerDay.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已完成的学习日无需重规划");
        }

        AdaptationMetadataDto adaptation = adaptiveLearningService.decide(
                request.getUserId(), "REPLAN", "plan:" + planId, plan.getGoalText());
        PlanResponse aiResponse = planReplanAiService.replan(plan, days, request, adaptation);
        if (aiResponse != null && aiResponse.getDays() != null && aiResponse.getDays().size() == days.size()) {
            applyAiReplanResult(plan, days, aiResponse, triggerDay);
            return aiResponse;
        }

        applyRuleBasedReplan(plan, days, triggerDay, request.getDelayDays(), adaptation);
        PlanResponse response = planQueryService.getPlanById(planId, request.getUserId());
        response.setAdaptation(adaptation);
        return response;
    }

    private void applyAiReplanResult(StudyPlan plan,
                                     List<StudyPlanDay> days,
                                     PlanResponse response,
                                     StudyPlanDay triggerDay) {
        Map<Integer, StudyPlanDay> dayMap = new HashMap<>();
        for (StudyPlanDay day : days) {
            if (day.getDayIndex() != null) {
                dayMap.put(day.getDayIndex(), day);
            }
        }

        for (int i = 0; i < response.getDays().size(); i++) {
            PlanDayDto dto = response.getDays().get(i);
            Integer dayIndex = dto.getDayIndex() != null ? dto.getDayIndex() : i + 1;
            StudyPlanDay entity = dayMap.get(dayIndex);
            if (entity == null) {
                continue;
            }

            if ("COMPLETED".equalsIgnoreCase(entity.getStatus())) {
                dto.setId(entity.getId());
                dto.setDate(entity.getDate());
                dto.setTitle(entity.getTitle());
                dto.setTasks(readTasks(entity.getTasksJson()));
                dto.setStatus("completed");
                dto.setDayIndex(entity.getDayIndex());
                continue;
            }

            if (dto.getDate() != null) {
                entity.setDate(dto.getDate());
            }
            if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
                entity.setTitle(dto.getTitle());
            }
            entity.setTasksJson(writeTasks(dto.getTasks()));

            String nextStatus = dto.getStatus();
            if (nextStatus != null && !nextStatus.isBlank()) {
                entity.setStatus(nextStatus.toUpperCase());
            } else if (Objects.equals(entity.getId(), triggerDay.getId())) {
                entity.setStatus("DELAYED");
                dto.setStatus("delayed");
            } else {
                entity.setStatus("NOT_STARTED");
                dto.setStatus("not_started");
            }

            dto.setId(entity.getId());
            dto.setDayIndex(entity.getDayIndex());
            dto.setDate(entity.getDate());
        }

        studyPlanDayRepository.saveAll(days);
        applyAdaptation(plan, response.getAdaptation());
        refreshPlanRange(plan, days);
        response.setPlanId(String.valueOf(plan.getId()));
        response.setStartDate(plan.getStartDate());
        response.setEndDate(plan.getEndDate());
    }

    private void applyRuleBasedReplan(StudyPlan plan,
                                      List<StudyPlanDay> days,
                                      StudyPlanDay triggerDay,
                                      Integer requestDelayDays,
                                      AdaptationMetadataDto adaptation) {
        int startIndex = days.indexOf(triggerDay);
        int delayDays = requestDelayDays != null && requestDelayDays > 0 ? requestDelayDays : 1;

        LocalDate previousDate = startIndex > 0 ? days.get(startIndex - 1).getDate() : null;
        LocalDate fallbackStart = triggerDay.getDate() != null ? triggerDay.getDate() : LocalDate.now();
        LocalDate cursor = previousDate != null ? previousDate : fallbackStart.minusDays(1);
        LocalDate nextDate = fallbackStart.plusDays(delayDays);

        for (int i = startIndex; i < days.size(); i++) {
            StudyPlanDay day = days.get(i);
            if ("COMPLETED".equalsIgnoreCase(day.getStatus())) {
                if (day.getDate() != null && day.getDate().isAfter(cursor)) {
                    cursor = day.getDate();
                }
                continue;
            }

            if (nextDate.isBefore(cursor.plusDays(1))) {
                nextDate = cursor.plusDays(1);
            }

            if (Objects.equals(day.getId(), triggerDay.getId())) {
                day.setStatus("DELAYED");
            } else {
                day.setStatus("NOT_STARTED");
            }

            day.setDate(nextDate);
            cursor = nextDate;
            nextDate = nextDate.plusDays(1);
        }

        if (Boolean.TRUE.equals(adaptation.getApplied()) && !adaptation.getWeakPoints().isEmpty()) {
            StudyPlanDay firstPending = days.stream()
                    .filter(day -> !"COMPLETED".equalsIgnoreCase(day.getStatus()))
                    .findFirst().orElse(null);
            if (firstPending != null) {
                List<String> tasks = new java.util.ArrayList<>(readTasks(firstPending.getTasksJson()));
                String weakPoint = adaptation.getWeakPoints().get(0).getDisplayName();
                String reviewTask = "优先复习：" + weakPoint + "（建议间隔 " + adaptation.getReviewIntervalDays() + " 天）";
                if (!tasks.contains(reviewTask)) tasks.add(0, reviewTask);
                firstPending.setTasksJson(writeTasks(tasks));
            }
        }
        studyPlanDayRepository.saveAll(days);
        applyAdaptation(plan, adaptation);
        refreshPlanRange(plan, days);
    }

    private void applyAdaptation(StudyPlan plan, AdaptationMetadataDto adaptation) {
        if (adaptation == null) return;
        plan.setAdaptationPolicyVersion(adaptation.getPolicyVersion());
        plan.setAdaptationVariant(adaptation.getVariant());
        plan.setAdaptationApplied(Boolean.TRUE.equals(adaptation.getApplied()));
        plan.setAdaptationReason(adaptation.getReason());
    }

    private void refreshPlanRange(StudyPlan plan, List<StudyPlanDay> days) {
        LocalDate startDate = days.stream()
                .map(StudyPlanDay::getDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(plan.getStartDate());
        LocalDate endDate = days.stream()
                .map(StudyPlanDay::getDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(plan.getEndDate());
        plan.setStartDate(startDate);
        plan.setEndDate(endDate);
        studyPlanRepository.save(plan);
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

    private String writeTasks(List<String> tasks) {
        try {
            return objectMapper.writeValueAsString(tasks != null ? tasks : Collections.emptyList());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}