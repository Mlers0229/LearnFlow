package com.learnflow.service;

import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.StudyPlanDayRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningProgressServiceTest {

    @Test
    void updatesOwnedDayAndRecordsOneTransition() {
        StudyPlanDayRepository repository = mock(StudyPlanDayRepository.class);
        MasteryService masteryService = mock(MasteryService.class);
        LearningProgressService service = new LearningProgressService(repository, masteryService);
        StudyPlan plan = new StudyPlan();
        plan.setUserId(7L);
        StudyPlanDay day = new StudyPlanDay();
        day.setId(12L);
        day.setPlan(plan);
        day.setTitle("Spring 事务");
        day.setStatus("IN_PROGRESS");
        day.setUpdatedAt(LocalDateTime.of(2026, 8, 23, 10, 30));
        when(repository.findById(12L)).thenReturn(Optional.of(day));

        service.updateDayStatus(12L, 7L, "COMPLETED");

        verify(repository).save(day);
        verify(masteryService).recordPlanDayStatus(
                7L, day, "IN_PROGRESS", "COMPLETED", "2026-08-23T10:30");
    }

    @Test
    void repeatedStatusIsIdempotent() {
        StudyPlanDayRepository repository = mock(StudyPlanDayRepository.class);
        MasteryService masteryService = mock(MasteryService.class);
        LearningProgressService service = new LearningProgressService(repository, masteryService);
        StudyPlan plan = new StudyPlan();
        plan.setUserId(7L);
        StudyPlanDay day = new StudyPlanDay();
        day.setPlan(plan);
        day.setStatus("COMPLETED");
        when(repository.findById(12L)).thenReturn(Optional.of(day));

        service.updateDayStatus(12L, 7L, "COMPLETED");

        verify(repository, never()).save(day);
        verify(masteryService, never()).recordPlanDayStatus(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesCrossUserTransition() {
        StudyPlanDayRepository repository = mock(StudyPlanDayRepository.class);
        LearningProgressService service = new LearningProgressService(repository, mock(MasteryService.class));
        StudyPlan plan = new StudyPlan();
        plan.setUserId(8L);
        StudyPlanDay day = new StudyPlanDay();
        day.setPlan(plan);
        when(repository.findById(12L)).thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.updateDayStatus(12L, 7L, "COMPLETED"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}

