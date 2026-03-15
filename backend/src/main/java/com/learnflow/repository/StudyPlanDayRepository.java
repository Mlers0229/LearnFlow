package com.learnflow.repository;

import com.learnflow.entity.StudyPlanDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyPlanDayRepository extends JpaRepository<StudyPlanDay, Long> {

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


