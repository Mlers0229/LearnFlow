package com.learnflow.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.dto.AdaptationMetadataDto;
import com.learnflow.dto.ExerciseEvaluateRequest;
import com.learnflow.dto.ExerciseEvaluateResponseDto;
import com.learnflow.dto.ExerciseQuestionDto;
import com.learnflow.dto.ExerciseRecordCreateRequest;
import com.learnflow.dto.GoalRequest;
import com.learnflow.dto.PlanDayDto;
import com.learnflow.dto.PlanProgressDto;
import com.learnflow.dto.PlanReplanRequest;
import com.learnflow.dto.PlanResponse;
import com.learnflow.dto.PlanSummaryDto;
import com.learnflow.dto.ResourceItemDto;
import com.learnflow.dto.UpdateDayStatusRequest;
import com.learnflow.dto.UpdatePlanRequest;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.service.AdaptiveLearningService;
import com.learnflow.service.AiProxyService;
import com.learnflow.service.ExerciseRecordService;
import com.learnflow.service.PlanQueryService;
import com.learnflow.service.PlanReplanService;
import com.learnflow.service.ResourceService;
import com.learnflow.service.CurrentUserService;
import com.learnflow.service.LearningProgressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学习计划相关接口。
 *
 * 第一版只提供一个简单的 /api/plan 接口：
 * - 接收用户的学习目标（GoalRequest）
 * - 调用 AiProxyService 生成示例计划
 * - 返回 PlanResponse，方便前端先联调展示
 *
 * 后续接入 FastAPI Agent 平台后，这里的路由保持稳定，
 * 只是 AiProxyService 的内部实现会逐步升级为真实 AI 调用。
 */
@RestController
@RequestMapping("/api")
public class PlanController {

    private final AiProxyService aiProxyService;

    private final PlanQueryService planQueryService;

    private final StudyPlanDayRepository studyPlanDayRepository;

    private final ExerciseRecordService exerciseRecordService;

    private final ResourceService resourceService;

    private final PlanReplanService planReplanService;

    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final LearningProgressService learningProgressService;
    private final AdaptiveLearningService adaptiveLearningService;

    public PlanController(AiProxyService aiProxyService,
                          PlanQueryService planQueryService,
                          StudyPlanDayRepository studyPlanDayRepository,
                          ExerciseRecordService exerciseRecordService,
                          ResourceService resourceService,
                          PlanReplanService planReplanService,
                          ObjectMapper objectMapper,
                          CurrentUserService currentUserService,
                          LearningProgressService learningProgressService,
                          AdaptiveLearningService adaptiveLearningService) {
        this.aiProxyService = aiProxyService;
        this.planQueryService = planQueryService;
        this.studyPlanDayRepository = studyPlanDayRepository;
        this.exerciseRecordService = exerciseRecordService;
        this.resourceService = resourceService;
        this.planReplanService = planReplanService;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
        this.learningProgressService = learningProgressService;
        this.adaptiveLearningService = adaptiveLearningService;
    }

    @PostMapping("/plan")
    public ResponseEntity<PlanResponse> generatePlan(@Valid @RequestBody GoalRequest request) {
        request.setUserId(currentUserService.requireUserId());
        PlanResponse plan = aiProxyService.generatePlan(request);
        return new ResponseEntity<>(plan, HttpStatus.OK);
    }

    /**
     * 根据 planId 查询完整学习计划（包含每日任务）。
     */
    @GetMapping("/plan/{id}")
    public ResponseEntity<PlanResponse> getPlanById(@PathVariable("id") Long id) {
        PlanResponse plan = planQueryService.getPlanById(id, currentUserService.requireUserId());
        return new ResponseEntity<>(plan, HttpStatus.OK);
    }

    /**
     * 查询最近的学习计划列表，用于前端历史计划页。
     */
    @GetMapping("/plan/recent")
    public ResponseEntity<List<PlanSummaryDto>> getRecentPlans(
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        List<PlanSummaryDto> plans = planQueryService.getRecentPlans(currentUserService.requireUserId(), limit);
        return new ResponseEntity<>(plans, HttpStatus.OK);
    }

    /**
     * 更新整份学习计划的基本信息（目前支持：标题与状态）。
     */
    @PatchMapping("/plan/{id}")
    public ResponseEntity<Void> updatePlan(@PathVariable("id") Long id,
                                           @Valid @RequestBody UpdatePlanRequest request) {
        StudyPlan plan = planQueryService
                .getPlanEntityByIdAndUser(id, currentUserService.requireUserId());

        String title = request.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            plan.setTitle(title.trim());
        }

        String status = request.getStatus();
        if (status != null && !status.trim().isEmpty()) {
            String normalized = status.trim().toLowerCase();
            if (normalized.equals("active")
                    || normalized.equals("completed")
                    || normalized.equals("cancelled")) {
                plan.setStatus(normalized);
            }
        }

        planQueryService.savePlan(plan);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * 删除整份学习计划（当前实现为软删：计划标记为 cancelled 并删除对应的 day 记录）。
     */
    @DeleteMapping("/plan/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable("id") Long id) {
        planQueryService.deletePlanForUser(id, currentUserService.requireUserId());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * 根据某一天的计划，推荐若干学习资源（按天推荐）。
     */
    @GetMapping("/plan/day/{dayId}/resources")
    public ResponseEntity<List<ResourceItemDto>> getResourcesForDay(@PathVariable("dayId") Long dayId) {
        Long userId = currentUserService.requireUserId();
        StudyPlanDay day = planQueryService.getDayEntityByIdAndUser(dayId, userId);
        StudyPlan plan = day.getPlan();
        Integer weekIndex = resolveWeekIndex(day);

        AdaptationMetadataDto adaptation = adaptiveLearningService.decide(
                userId, "RESOURCE", "day:" + dayId, day.getTitle());
        List<ResourceItemDto> resources = aiProxyService.recommendResources(
                day.getTitle(),
                plan != null ? plan.getLevel() : null,
                plan != null ? plan.getGoalText() : null,
                readTasksFromJson(day.getTasksJson()),
                resolveEstimatedMinutes(plan),
                buildPhaseTitle(day, plan),
                buildWeekTheme(weekIndex),
                inferTaskType(day),
                adaptation
        );
        return new ResponseEntity<>(resourceService.prepareRecommendedResources(resources, userId, 5), HttpStatus.OK);
    }

    /**
     * 针对整份学习计划，推荐若干学习资源（汇总推荐）。
     */
    @GetMapping("/plan/{id}/resources")
    public ResponseEntity<List<ResourceItemDto>> getResourcesForPlan(@PathVariable("id") Long id) {
        Long userId = currentUserService.requireUserId();
        StudyPlan plan = planQueryService.getPlanEntityByIdAndUser(id, userId);
        String topicText = planQueryService.buildPlanTopicText(id);

        AdaptationMetadataDto adaptation = adaptiveLearningService.decide(
                userId, "RESOURCE", "plan:" + id, topicText);
        List<ResourceItemDto> resources = aiProxyService.recommendResources(
                topicText,
                plan.getLevel(),
                plan.getGoalText(),
                planQueryService.buildPlanTaskTexts(id),
                resolveEstimatedMinutes(plan),
                "全局规划",
                buildPlanWeekTheme(plan),
                "plan_overview",
                adaptation
        );
        return new ResponseEntity<>(resourceService.prepareRecommendedResources(resources, userId, 5), HttpStatus.OK);
    }

    /**
     * 更新某一天学习计划的状态（打卡用）。
     *
     * 支持的状态值（不区分大小写）：
     * - not_started / in_progress / completed / delayed
     */
    @PatchMapping("/plan/day/{dayId}/status")
    public ResponseEntity<Void> updateDayStatus(@PathVariable("dayId") Long dayId,
                                                @Valid @RequestBody UpdateDayStatusRequest request) {
        String rawStatus = request.getStatus();
        if (rawStatus == null || rawStatus.isBlank()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        String normalized = rawStatus.trim().toUpperCase();
        // 简单校验，只允许几种合法值
        if (!normalized.equals("NOT_STARTED")
                && !normalized.equals("IN_PROGRESS")
                && !normalized.equals("COMPLETED")
                && !normalized.equals("DELAYED")) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        learningProgressService.updateDayStatus(dayId, currentUserService.requireUserId(), normalized);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * 从某一天开始顺延并重排后续未完成任务。
     */
    @PostMapping("/plan/{id}/replan")
    public ResponseEntity<PlanResponse> replanPlan(@PathVariable("id") Long id,
                                                   @Valid @RequestBody PlanReplanRequest request) {
        request.setUserId(currentUserService.requireUserId());
        PlanResponse response = planReplanService.replan(id, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 查询某个学习计划的整体完成情况（用于进度条展示）。
     */
    @GetMapping("/plan/{id}/progress")
    public ResponseEntity<PlanProgressDto> getPlanProgress(@PathVariable("id") Long id) {
        planQueryService.getPlanEntityByIdAndUser(id, currentUserService.requireUserId());
        List<StudyPlanDay> days = studyPlanDayRepository.findByPlan_IdOrderByDayIndexAsc(id);
        PlanProgressDto dto = new PlanProgressDto();
        int total = days.size();
        dto.setTotalDays(total);

        int completed = 0;
        int inProgress = 0;
        int notStarted = 0;
        int delayed = 0;

        for (StudyPlanDay d : days) {
            String status = d.getStatus();
            if (status == null) {
                notStarted++;
                continue;
            }
            switch (status) {
                case "COMPLETED" -> completed++;
                case "IN_PROGRESS" -> inProgress++;
                case "DELAYED" -> delayed++;
                default -> notStarted++;
            }
        }

        dto.setCompletedDays(completed);
        dto.setInProgressDays(inProgress);
        dto.setNotStartedDays(notStarted);
        dto.setDelayedDays(delayed);

        int rate = 0;
        if (total > 0) {
            rate = (int) Math.round(completed * 100.0 / total);
        }
        dto.setCompletionRate(rate);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    /**
     * 细化某一天的学习任务（从粗略版升级为更具体的版本）。
     *
     * 调用流程：Java 后端从数据库读取该天与计划信息 -> 调用 FastAPI DetailPlanAgent ->
     * 用新任务列表覆盖 tasksJson，并返回最新的 PlanDayDto 给前端。
     */
    @PostMapping("/plan/day/{dayId}/refine")
    public ResponseEntity<PlanDayDto> refineDay(@PathVariable("dayId") Long dayId) {
        StudyPlanDay day = planQueryService.getDayEntityByIdAndUser(dayId, currentUserService.requireUserId());
        StudyPlan plan = day.getPlan();

        List<String> currentTasks = readTasksFromJson(day.getTasksJson());

        String goalText = plan != null ? plan.getGoalText() : null;
        Integer hoursPerDay = plan != null ? plan.getHoursPerDay() : null;
        String level = plan != null ? plan.getLevel() : null;

        List<String> refinedTasks = aiProxyService.refineDayTasks(
                day.getTitle(),
                currentTasks,
                goalText,
                hoursPerDay,
                level
        );

        // 覆盖保存到数据库
        try {
            day.setTasksJson(objectMapper.writeValueAsString(refinedTasks));
        } catch (JsonProcessingException e) {
            // 如果序列化失败，就退回原列表
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        StudyPlanDay saved = studyPlanDayRepository.save(day);

        // 构造返回给前端的最新 PlanDayDto
        PlanDayDto dto = new PlanDayDto();
        dto.setId(saved.getId());
        dto.setDate(saved.getDate());
        dto.setTitle(saved.getTitle());
        dto.setTasks(refinedTasks);
        String status = saved.getStatus();
        if (status != null) {
            dto.setStatus(status.toLowerCase());
        } else {
            dto.setStatus("not_started");
        }

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    /**
     * 为某一天生成练习题（调用 TutorAgent v2）。
     *
     * 典型调用场景：前端在每日任务卡片中点击“生成练习题”。
     */
    @GetMapping("/plan/day/{dayId}/exercises")
    public ResponseEntity<List<ExerciseQuestionDto>> getExercisesForDay(@PathVariable("dayId") Long dayId) {
        Long userId = currentUserService.requireUserId();
        StudyPlanDay day = planQueryService.getDayEntityByIdAndUser(dayId, userId);
        StudyPlan plan = day.getPlan();
        Integer weekIndex = resolveWeekIndex(day);

        AdaptationMetadataDto adaptation = adaptiveLearningService.decide(
                userId, "EXERCISE", "day:" + dayId, day.getTitle());
        List<ExerciseQuestionDto> questions = aiProxyService.generateExercises(
                day.getTitle(),
                plan != null ? plan.getGoalText() : null,
                plan != null ? plan.getLevel() : null,
                buildPhaseTitle(day, plan),
                weekIndex,
                day.getDayIndex(),
                inferTaskType(day),
                resolveQuestionCount(plan),
                adaptation
        );
        return new ResponseEntity<>(questions, HttpStatus.OK);
    }

    /**
     * 评估某一天的一道练习作答，返回得分与反馈。
     */
    @PostMapping("/plan/day/{dayId}/exercise-evaluate")
    public ResponseEntity<ExerciseEvaluateResponseDto> evaluateExerciseForDay(
            @PathVariable("dayId") Long dayId,
            @Valid @RequestBody ExerciseEvaluateRequest request) {
        StudyPlanDay day = planQueryService.getDayEntityByIdAndUser(dayId, currentUserService.requireUserId());
        StudyPlan plan = day.getPlan();

        ExerciseEvaluateResponseDto response = aiProxyService.evaluateExercise(
                day.getTitle(),
                plan != null ? plan.getGoalText() : null,
                plan != null ? plan.getLevel() : null,
                buildPhaseTitle(day, plan),
                request.getQuestion(),
                request.getReferenceAnswer(),
                request.getUserAnswer()
        );
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 保存某一天下的练习记录（用户在前端填写答案后调用）。
     *
     * 说明：
     * - dayId 通过路径传入；
     * - request 中包含题干、参考答案、用户作答等信息；
     * - 当前仅做简单落库，不返回记录内容。
     */
    @PostMapping("/plan/day/{dayId}/exercise-records")
    public ResponseEntity<Void> saveExerciseRecord(@PathVariable("dayId") Long dayId,
                                                   @Valid @RequestBody ExerciseRecordCreateRequest request) {
        try {
            exerciseRecordService.saveRecord(dayId, currentUserService.requireUserId(), request);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    private List<String> readTasksFromJson(String tasksJson) {
        if (tasksJson == null || tasksJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    tasksJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Integer resolveEstimatedMinutes(StudyPlan plan) {
        if (plan == null || plan.getHoursPerDay() == null) {
            return null;
        }
        return plan.getHoursPerDay() * 60;
    }

    private Integer resolveWeekIndex(StudyPlanDay day) {
        if (day == null || day.getDayIndex() == null || day.getDayIndex() <= 0) {
            return null;
        }
        return ((day.getDayIndex() - 1) / 7) + 1;
    }

    private String buildWeekTheme(Integer weekIndex) {
        if (weekIndex == null) {
            return null;
        }
        return "第" + weekIndex + "周";
    }

    private String buildPlanWeekTheme(StudyPlan plan) {
        if (plan == null || plan.getDurationWeeks() == null) {
            return null;
        }
        return plan.getDurationWeeks() + "周学习计划";
    }

    private String buildPhaseTitle(StudyPlanDay day, StudyPlan plan) {
        Integer weekIndex = resolveWeekIndex(day);
        if (weekIndex == null || plan == null || plan.getDurationWeeks() == null || plan.getDurationWeeks() <= 0) {
            return null;
        }

        int durationWeeks = plan.getDurationWeeks();
        int firstBoundary = Math.max(1, (int) Math.ceil(durationWeeks / 3.0));
        int secondBoundary = Math.max(firstBoundary + 1, (int) Math.ceil(durationWeeks * 2.0 / 3.0));

        if (weekIndex <= firstBoundary) {
            return "基础搭建";
        }
        if (weekIndex <= secondBoundary) {
            return "能力强化";
        }
        return "综合应用";
    }

    private String inferTaskType(StudyPlanDay day) {
        StringBuilder text = new StringBuilder();
        if (day.getTitle() != null) {
            text.append(day.getTitle()).append(" ");
        }
        for (String task : readTasksFromJson(day.getTasksJson())) {
            text.append(task).append(" ");
        }

        String normalized = text.toString().toLowerCase();
        if (normalized.contains("项目") || normalized.contains("实战") || normalized.contains("project")) {
            return "project";
        }
        if (normalized.contains("练习") || normalized.contains("刷题") || normalized.contains("practice") || normalized.contains("习题")) {
            return "practice";
        }
        if (normalized.contains("复习") || normalized.contains("review") || normalized.contains("总结")) {
            return "review";
        }
        if (normalized.contains("调试") || normalized.contains("debug")) {
            return "debug";
        }
        return "learn";
    }

    private Integer resolveQuestionCount(StudyPlan plan) {
        if (plan == null || plan.getHoursPerDay() == null) {
            return 2;
        }
        return plan.getHoursPerDay() >= 2 ? 3 : 2;
    }
}



