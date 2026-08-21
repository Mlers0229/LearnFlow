package com.learnflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.repository.StudyPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanQueryServiceOwnershipTest {

    @Test
    void hidesDayWhenItBelongsToAnotherUser() {
        StudyPlanRepository planRepository = mock(StudyPlanRepository.class);
        StudyPlanDayRepository dayRepository = mock(StudyPlanDayRepository.class);
        PlanQueryService service = new PlanQueryService(planRepository, dayRepository, new ObjectMapper());
        StudyPlan plan = new StudyPlan();
        plan.setUserId(7L);
        StudyPlanDay day = new StudyPlanDay();
        day.setPlan(plan);
        when(dayRepository.findById(99L)).thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.getDayEntityByIdAndUser(99L, 8L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void returnsDayForItsOwner() {
        StudyPlanRepository planRepository = mock(StudyPlanRepository.class);
        StudyPlanDayRepository dayRepository = mock(StudyPlanDayRepository.class);
        PlanQueryService service = new PlanQueryService(planRepository, dayRepository, new ObjectMapper());
        StudyPlan plan = new StudyPlan();
        plan.setUserId(7L);
        StudyPlanDay day = new StudyPlanDay();
        day.setPlan(plan);
        when(dayRepository.findById(99L)).thenReturn(Optional.of(day));

        assertThat(service.getDayEntityByIdAndUser(99L, 7L)).isSameAs(day);
    }
}
