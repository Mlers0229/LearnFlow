package com.learnflow.repository;

import com.learnflow.entity.ExerciseRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

/**
 * 练习记录仓库。
 */
public interface ExerciseRecordRepository extends JpaRepository<ExerciseRecord, Long> {

    Optional<ExerciseRecord> findByIdAndUser_Id(Long id, Long userId);

    Page<ExerciseRecord> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<ExerciseRecord> findByUser_IdAndPlanDay_Plan_IdOrderByCreatedAtDesc(Long userId,
                                                                             Long planId,
                                                                             Pageable pageable);

    Page<ExerciseRecord> findByUser_IdAndPlanDay_IdOrderByCreatedAtDesc(Long userId,
                                                                        Long dayId,
                                                                        Pageable pageable);

    long deleteByUser_IdAndPlanDay_Id(Long userId, Long dayId);

    List<ExerciseRecord> findAllByUser_IdAndPlanDay_Id(Long userId, Long dayId);
}
