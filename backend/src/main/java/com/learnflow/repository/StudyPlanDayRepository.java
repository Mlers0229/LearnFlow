package com.learnflow.repository;

import com.learnflow.entity.StudyPlanDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudyPlanDayRepository extends JpaRepository<StudyPlanDay, Long> {

    @Query("""
            select day
            from StudyPlanDay day
            join fetch day.plan plan
            where day.id = :dayId and plan.userId = :userId
            """)
    Optional<StudyPlanDay> findOwnedByIdWithPlan(@Param("dayId") Long dayId,
                                                 @Param("userId") Long userId);

    /**
     * 根据计划 ID 查询该计划下的所有天，按 dayIndex 升序排列。
     *
     * @param planId 计划 ID（study_plan.id）
     * @return 对应的每日计划列表
     */
    List<StudyPlanDay> findByPlan_IdOrderByDayIndexAsc(Long planId);

    /**
     * 删除某个计划下的所有 study_plan_day 记录。
     */
    void deleteByPlan_Id(Long planId);
}


