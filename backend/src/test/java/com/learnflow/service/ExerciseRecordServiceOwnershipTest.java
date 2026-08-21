package com.learnflow.service;

import com.learnflow.dto.ExerciseRecordCreateRequest;
import com.learnflow.entity.ExerciseRecord;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.ExerciseRecordRepository;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExerciseRecordServiceOwnershipTest {

    @Test
    void refusesToSaveExerciseAgainstAnotherUsersPlanDay() {
        ExerciseRecordRepository recordRepository = mock(ExerciseRecordRepository.class);
        StudyPlanDayRepository dayRepository = mock(StudyPlanDayRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ExerciseRecordService service = new ExerciseRecordService(recordRepository, dayRepository, userRepository);
        StudyPlan otherUsersPlan = new StudyPlan();
        otherUsersPlan.setUserId(8L);
        StudyPlanDay day = new StudyPlanDay();
        day.setPlan(otherUsersPlan);
        when(dayRepository.findById(99L)).thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.saveRecord(99L, 7L, new ExerciseRecordCreateRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dayId");

        verify(recordRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordDeletionUsesOwnerScopedRepositoryLookup() {
        ExerciseRecordRepository recordRepository = mock(ExerciseRecordRepository.class);
        ExerciseRecordService service = new ExerciseRecordService(
                recordRepository,
                mock(StudyPlanDayRepository.class),
                mock(UserRepository.class)
        );
        ExerciseRecord record = new ExerciseRecord();
        when(recordRepository.findByIdAndUser_Id(12L, 7L)).thenReturn(Optional.of(record));

        service.deleteRecord(12L, 7L);

        verify(recordRepository).findByIdAndUser_Id(12L, 7L);
        verify(recordRepository).delete(record);
    }
}
