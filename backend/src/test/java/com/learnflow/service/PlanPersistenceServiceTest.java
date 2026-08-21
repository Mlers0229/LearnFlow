package com.learnflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.dto.GoalRequest;
import com.learnflow.dto.PlanDayDto;
import com.learnflow.dto.PlanResponse;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.repository.StudyPlanRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanPersistenceServiceTest {

    @Test
    void sourceTaskPreventsDuplicatePlanWrites() {
        StudyPlanRepository plans = mock(StudyPlanRepository.class);
        StudyPlanDayRepository days = mock(StudyPlanDayRepository.class);
        PlanPersistenceService service = new PlanPersistenceService(plans, days, new ObjectMapper());
        UUID taskId = UUID.randomUUID();
        StudyPlan existing = new StudyPlan();
        existing.setId(42L);
        when(plans.findBySourceTaskId(taskId)).thenReturn(Optional.of(existing));
        PlanResponse response = new PlanResponse();

        Long result = service.persist(goal(), response, taskId);

        assertThat(result).isEqualTo(42L);
        assertThat(response.getPlanId()).isEqualTo("42");
        verify(plans, never()).save(any());
    }

    @Test
    void persistsPlanAndDaysAtomicallyThroughSingleServiceBoundary() {
        StudyPlanRepository plans = mock(StudyPlanRepository.class);
        StudyPlanDayRepository days = mock(StudyPlanDayRepository.class);
        PlanPersistenceService service = new PlanPersistenceService(plans, days, new ObjectMapper());
        UUID taskId = UUID.randomUUID();
        when(plans.findBySourceTaskId(taskId)).thenReturn(Optional.empty());
        when(plans.save(any(StudyPlan.class))).thenAnswer(invocation -> {
            StudyPlan plan = invocation.getArgument(0);
            plan.setId(9L);
            return plan;
        });
        when(days.save(any(StudyPlanDay.class))).thenAnswer(invocation -> {
            StudyPlanDay day = invocation.getArgument(0);
            day.setId(10L);
            return day;
        });
        PlanDayDto day = new PlanDayDto();
        day.setTitle("Day 1");
        day.setDate(LocalDate.now());
        day.setTasks(List.of("task"));
        PlanResponse response = new PlanResponse();
        response.setTitle("Plan");
        response.setDays(List.of(day));

        Long result = service.persist(goal(), response, taskId);

        assertThat(result).isEqualTo(9L);
        assertThat(day.getId()).isEqualTo(10L);
        verify(days).save(any(StudyPlanDay.class));
    }

    private static GoalRequest goal() {
        GoalRequest request = new GoalRequest();
        request.setUserId(7L);
        request.setGoalText("Java");
        request.setDurationWeeks(4);
        request.setHoursPerDay(1);
        request.setLevel("beginner");
        return request;
    }
}
