package com.learnflow.repository;

import com.learnflow.entity.StudyPlan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    /**
     * 按创建时间倒序查询学习计划列表。
     *
     * @param pageable 分页参数，用于控制返回条数
     * @return 最近的学习计划列表
     */
    List<StudyPlan> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 根据 planId 与 userId 精确查找单个学习计划。
     */
    Optional<StudyPlan> findByIdAndUserId(Long id, Long userId);

    /**
     * 查询某个用户最近的学习计划列表（按创建时间倒序）。
     */
    List<StudyPlan> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<StudyPlan> findBySourceTaskId(UUID sourceTaskId);
}

