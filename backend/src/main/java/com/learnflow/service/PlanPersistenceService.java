package com.learnflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.dto.AdaptationMetadataDto;
import com.learnflow.dto.GoalRequest;
import com.learnflow.dto.PlanDayDto;
import com.learnflow.dto.PlanResponse;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.repository.StudyPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlanPersistenceService {

    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanDayRepository studyPlanDayRepository;
    private final ObjectMapper objectMapper;

    public PlanPersistenceService(
            StudyPlanRepository studyPlanRepository,
            StudyPlanDayRepository studyPlanDayRepository,
            ObjectMapper objectMapper
    ) {
        this.studyPlanRepository = studyPlanRepository;
        this.studyPlanDayRepository = studyPlanDayRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long persist(GoalRequest request, PlanResponse planResponse, UUID sourceTaskId) {
        if (sourceTaskId != null) {
            StudyPlan existing = studyPlanRepository.findBySourceTaskId(sourceTaskId).orElse(null);
            if (existing != null) {
                planResponse.setPlanId(String.valueOf(existing.getId()));
                return existing.getId();
            }
        }

        StudyPlan plan = new StudyPlan();
        plan.setUserId(request.getUserId());
        plan.setGoalText(request.getGoalText());
        plan.setTitle(planResponse.getTitle());
        plan.setDurationWeeks(request.getDurationWeeks());
        plan.setHoursPerDay(request.getHoursPerDay());
        plan.setLevel(request.getLevel());
        plan.setStartDate(planResponse.getStartDate());
        plan.setEndDate(planResponse.getEndDate());
        plan.setStatus("active");
        plan.setSourceTaskId(sourceTaskId);
        AdaptationMetadataDto adaptation = planResponse.getAdaptation();
        if (adaptation != null) {
            plan.setAdaptationPolicyVersion(adaptation.getPolicyVersion());
            plan.setAdaptationVariant(adaptation.getVariant());
            plan.setAdaptationApplied(Boolean.TRUE.equals(adaptation.getApplied()));
            plan.setAdaptationReason(adaptation.getReason());
        }

        StudyPlan savedPlan = studyPlanRepository.save(plan);
        if (planResponse.getDays() != null) {
            for (int index = 0; index < planResponse.getDays().size(); index++) {
                PlanDayDto dto = planResponse.getDays().get(index);
                StudyPlanDay day = new StudyPlanDay();
                day.setPlan(savedPlan);
                day.setDayIndex(dto.getDayIndex() != null ? dto.getDayIndex() : index + 1);
                day.setDate(dto.getDate());
                day.setTitle(dto.getTitle());
                day.setTasksJson(serializeTasks(dto));
                day.setStatus(dto.getStatus() == null ? "NOT_STARTED" : dto.getStatus().toUpperCase());
                StudyPlanDay savedDay = studyPlanDayRepository.save(day);
                dto.setId(savedDay.getId());
            }
        }

        planResponse.setPlanId(String.valueOf(savedPlan.getId()));
        return savedPlan.getId();
    }

    private String serializeTasks(PlanDayDto day) {
        try {
            return objectMapper.writeValueAsString(day.getTasks());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize generated plan tasks", exception);
        }
    }
}
