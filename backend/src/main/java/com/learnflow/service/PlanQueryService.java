package com.learnflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.dto.PlanDayDto;
import com.learnflow.dto.PlanResponse;
import com.learnflow.dto.PlanSummaryDto;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.repository.StudyPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学习计划查询相关的业务逻辑。
 *
 * - 根据 planId 查询完整计划结构（PlanResponse）
 * - 查询最近的计划概要列表（PlanSummaryDto）
 */
@Service
public class PlanQueryService {

    private static final Logger log = LoggerFactory.getLogger(PlanQueryService.class);

    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanDayRepository studyPlanDayRepository;
    private final ObjectMapper objectMapper;

    public PlanQueryService(StudyPlanRepository studyPlanRepository,
                            StudyPlanDayRepository studyPlanDayRepository,
                            ObjectMapper objectMapper) {
        this.studyPlanRepository = studyPlanRepository;
        this.studyPlanDayRepository = studyPlanDayRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据 planId 查询完整学习计划（限定在指定 userId 名下）。
     *
     * @param planId 计划 ID
     * @param userId 所属用户 ID
     * @return PlanResponse 结构，方便前端直接复用展示组件
     */
    public PlanResponse getPlanById(Long planId, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId 不能为空");
        }
        StudyPlan plan = studyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学习计划不存在或无权访问"));

        List<StudyPlanDay> days = studyPlanDayRepository.findByPlan_IdOrderByDayIndexAsc(planId);

        PlanResponse response = new PlanResponse();
        response.setPlanId(String.valueOf(plan.getId()));
        response.setTitle(plan.getTitle());
        response.setStartDate(plan.getStartDate());
        response.setEndDate(plan.getEndDate());

        List<PlanDayDto> dayDtos = days.stream()
                .map(this::mapToDayDto)
                .collect(Collectors.toList());
        response.setDays(dayDtos);

        return response;
    }

    /**
     * 查询某个用户最近的学习计划列表（按创建时间倒序）。
     *
     * @param userId 所属用户 ID
     * @param limit  返回条数
     * @return 计划概要列表
     */
    public List<PlanSummaryDto> getRecentPlans(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return Collections.emptyList();
        }

        List<StudyPlan> plans = studyPlanRepository.findAllByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(0, limit)
        );
        return plans.stream()
                .filter(plan -> plan.getStatus() == null
                        || !"cancelled".equalsIgnoreCase(plan.getStatus()))
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    /**
     * 构造用于整份计划资源推荐的「主题文本」，
     * 会综合计划的目标描述和每天的标题。
     */
    public String buildPlanTopicText(Long planId) {
        StudyPlan plan = getPlanEntityById(planId);
        List<StudyPlanDay> days = studyPlanDayRepository.findByPlan_IdOrderByDayIndexAsc(planId);

        StringBuilder sb = new StringBuilder();
        if (plan.getGoalText() != null) {
            sb.append(plan.getGoalText()).append(" ");
        }
        if (plan.getTitle() != null) {
            sb.append(plan.getTitle()).append(" ");
        }
        for (StudyPlanDay day : days) {
            if (day.getTitle() != null) {
                sb.append(day.getTitle()).append(" ");
            }
            for (String task : readTasks(day.getTasksJson())) {
                sb.append(task).append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * 构造整份计划的任务文本列表，用于资源推荐时补充上下文。
     */
    public List<String> buildPlanTaskTexts(Long planId) {
        List<StudyPlanDay> days = studyPlanDayRepository.findByPlan_IdOrderByDayIndexAsc(planId);
        List<String> taskTexts = new ArrayList<>();
        for (StudyPlanDay day : days) {
            if (day.getTitle() != null && !day.getTitle().isBlank()) {
                taskTexts.add(day.getTitle());
            }
            taskTexts.addAll(readTasks(day.getTasksJson()));
        }
        return taskTexts;
    }

    /**
     * 获取计划的学习者基础水平，用于资源推荐中的难度匹配。
     */
    public String getPlanLevel(Long planId) {
        StudyPlan plan = getPlanEntityById(planId);
        return plan.getLevel();
    }

    /**
     * 仅按 planId 查询 StudyPlan 实体。
     */
    public StudyPlan getPlanEntityById(Long planId) {
        return studyPlanRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学习计划不存在"));
    }

    /**
     * 仅在内部使用：根据 planId + userId 查询 StudyPlan 实体。
     */
    public StudyPlan getPlanEntityByIdAndUser(Long planId, Long userId) {
        return studyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学习计划不存在或无权访问"));
    }

    public StudyPlanDay getDayEntityByIdAndUser(Long dayId, Long userId) {
        StudyPlanDay day = studyPlanDayRepository.findById(dayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学习日不存在或无权访问"));
        StudyPlan plan = day.getPlan();
        if (plan == null || userId == null || !userId.equals(plan.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "学习日不存在或无权访问");
        }
        return day;
    }

    /**
     * 仅在内部使用：保存 StudyPlan 实体。
     */
    public void savePlan(StudyPlan plan) {
        studyPlanRepository.save(plan);
    }

    /**
     * 删除指定用户名下的一份学习计划（软删：计划标记为 cancelled，并删除对应的天记录）。
     */
    @Transactional
    public void deletePlanForUser(Long planId, Long userId) {
        StudyPlan plan = studyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学习计划不存在或无权访问"));

        // 删除该计划下的所有 day 记录
        studyPlanDayRepository.deleteByPlan_Id(planId);

        // 将计划状态标记为 cancelled，并保存
        plan.setStatus("cancelled");
        studyPlanRepository.save(plan);
    }

    private PlanDayDto mapToDayDto(StudyPlanDay entity) {
        PlanDayDto dto = new PlanDayDto();
        dto.setId(entity.getId());
        dto.setDate(entity.getDate());
        dto.setTitle(entity.getTitle());
        dto.setTasks(readTasks(entity.getTasksJson()));
        dto.setDayIndex(entity.getDayIndex());
        dto.setWeekIndex(resolveWeekIndex(entity));

        String status = entity.getStatus();
        if (status != null) {
            dto.setStatus(status.toLowerCase());
        } else {
            dto.setStatus("not_started");
        }
        return dto;
    }

    private Integer resolveWeekIndex(StudyPlanDay entity) {
        if (entity == null || entity.getDayIndex() == null || entity.getDayIndex() <= 0) {
            return null;
        }
        return ((entity.getDayIndex() - 1) / 7) + 1;
    }

    private List<String> readTasks(String tasksJson) {
        if (tasksJson == null || tasksJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tasksJson, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException e) {
            log.error("反序列化 tasksJson 失败，将返回空列表。payloadLength={}", tasksJson.length(), e);
            return Collections.emptyList();
        }
    }

    private PlanSummaryDto mapToSummaryDto(StudyPlan plan) {
        PlanSummaryDto dto = new PlanSummaryDto();
        dto.setId(plan.getId());
        dto.setTitle(plan.getTitle());
        dto.setStartDate(plan.getStartDate());
        dto.setEndDate(plan.getEndDate());
        dto.setStatus(plan.getStatus());
        dto.setCreatedAt(plan.getCreatedAt());
        return dto;
    }
}
