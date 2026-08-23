package com.learnflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnflow.dto.AdaptationMetadataDto;
import com.learnflow.dto.AgentCallLogDto;
import com.learnflow.dto.ExerciseEvaluateResponseDto;
import com.learnflow.dto.ExerciseQuestionDto;
import com.learnflow.dto.GoalRequest;
import com.learnflow.dto.PlanDayDto;
import com.learnflow.dto.PlanResponse;
import com.learnflow.dto.ResourceEvidenceDto;
import com.learnflow.dto.ResourceItemDto;
import com.learnflow.dto.agent.AgentDetailPlanRefineRequest;
import com.learnflow.dto.agent.AgentPlanGenerateRequest;
import com.learnflow.dto.agent.AgentDetailPlanRefineResponse;
import com.learnflow.dto.agent.AgentResourceEvidence;
import com.learnflow.dto.agent.AgentResourceItem;
import com.learnflow.dto.agent.AgentResourceQueryRequest;
import com.learnflow.dto.agent.AgentResourceRecommendResponse;
import com.learnflow.dto.agent.AgentTutorAttempt;
import com.learnflow.dto.agent.AgentTutorEvaluateRequest;
import com.learnflow.dto.agent.AgentTutorEvaluateResponse;
import com.learnflow.dto.agent.AgentTutorExerciseResponse;
import com.learnflow.dto.agent.AgentTutorGenerateRequest;
import com.learnflow.dto.agent.AgentTutorQuestion;
import com.learnflow.dto.agent.AgentTutorSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AiProxyService 负责与 AI Agent 平台（FastAPI）交互。
 */
@Service
public class AiProxyService {

    private static final Logger log = LoggerFactory.getLogger(AiProxyService.class);

    private final AgentHttpClient agentHttpClient;
    private final ObjectMapper objectMapper;
    private final PlanPersistenceService planPersistenceService;
    private final AdaptiveLearningService adaptiveLearningService;

    public AiProxyService(AgentHttpClient agentHttpClient,
                          ObjectMapper objectMapper,
                          PlanPersistenceService planPersistenceService,
                          AdaptiveLearningService adaptiveLearningService) {
        this.agentHttpClient = agentHttpClient;
        this.objectMapper = objectMapper;
        this.planPersistenceService = planPersistenceService;
        this.adaptiveLearningService = adaptiveLearningService;
    }

    /**
     * 根据用户提交的学习目标，调用 AI 平台生成学习计划。
     */
    public PlanResponse generatePlan(GoalRequest request) {
        PlanResponse response = generatePlanDraft(request);
        planPersistenceService.persist(request, response, null);
        return response;
    }

    /**
     * Generate a plan without writing business data. Async workers use this
     * boundary so cancellation can be checked before the atomic persistence step.
     */
    public PlanResponse generatePlanDraft(GoalRequest request) {
        return generatePlanDraft(request, null);
    }

    public PlanResponse generatePlanDraft(GoalRequest request, UUID taskId) {
        String url = "/api/v2/plan";
        AdaptationMetadataDto adaptation = adaptiveLearningService.decide(
                request.getUserId(), "PLAN", taskId == null ? "sync" : taskId.toString(), request.getGoalText());

        AgentPlanGenerateRequest payload = new AgentPlanGenerateRequest();
        payload.setGoalText(request.getGoalText());
        payload.setDurationWeeks(request.getDurationWeeks());
        payload.setHoursPerDay(request.getHoursPerDay());
        payload.setLevel(request.getLevel());
        payload.setTargetRole(request.getTargetRole());
        payload.setPreferredStyle(request.getPreferredStyle());
        payload.setConstraints(request.getConstraints());
        payload.setFinalDeliverable(request.getFinalDeliverable());
        payload.setWorkflowId(taskId == null ? null : taskId.toString());
        payload.setAdaptiveContext(adaptation);

        try {
            String rawResponse = taskId == null
                    ? postJson(AgentOperation.PLAN, url, payload, String.class)
                    : agentHttpClient.postJsonForTask(taskId, AgentOperation.PLAN, url, payload, String.class);
            PlanResponse response = parsePlanResponse(rawResponse);
            if (response != null) {
                response.setAdaptation(adaptation);
                return response;
            }
            if (taskId != null) {
                throw new RestClientException("Stateful Agent workflow returned an empty plan");
            }
            log.warn("调用 AI Agent 平台返回空响应，使用本地示例计划作为回退方案。");
        } catch (RestClientException e) {
            if (taskId != null) {
                throw e;
            }
            log.error("调用 AI Agent 平台失败，将使用本地示例计划。url={}", url, e);
        }

        PlanResponse fallback = buildFallbackPlan(request);
        fallback.setAdaptation(adaptation);
        return fallback;
    }

    public List<ResourceItemDto> recommendResources(String topic, String level) {
        return recommendResources(topic, level, null, null, null, null, null, null);
    }

    /**
     * 调用 FastAPI RagAgent v2，根据上下文推荐资源列表。
     */
    public List<ResourceItemDto> recommendResources(String topic,
                                                    String level,
                                                    String goalText,
                                                    List<String> taskTexts,
                                                    Integer estimatedMinutes,
                                                    String phaseTitle,
                                                    String weekTheme,
                                                    String taskType) {
        return recommendResources(topic, level, goalText, taskTexts, estimatedMinutes,
                phaseTitle, weekTheme, taskType, null);
    }

    public List<ResourceItemDto> recommendResources(String topic,
                                                    String level,
                                                    String goalText,
                                                    List<String> taskTexts,
                                                    Integer estimatedMinutes,
                                                    String phaseTitle,
                                                    String weekTheme,
                                                    String taskType,
                                                    AdaptationMetadataDto adaptiveContext) {
        String url = "/api/v2/rag/resources";
        String inferredDomain = inferLearningDomain(topic, goalText, taskTexts);
        AgentResourceQueryRequest payload = buildResourceQueryRequest(
                topic,
                level,
                inferredDomain,
                goalText,
                taskTexts,
                estimatedMinutes,
                phaseTitle,
                weekTheme,
                taskType,
                8
        );
        payload.setAdaptiveContext(adaptiveContext);

        try {
            AgentResourceRecommendResponse response = postJson(
                    AgentOperation.RAG,
                    url,
                    payload,
                    AgentResourceRecommendResponse.class
            );
            if (response == null || response.getResources() == null) {
                log.warn("RagAgent v2 返回空响应");
                return attachResourceAdaptation(recommendResourcesV1(topic, level), adaptiveContext);
            }

            return attachResourceAdaptation(response.getResources().stream()
                    .map(item -> mapToResourceItem(item, response.getQuerySummary()))
                    .toList(), adaptiveContext);
        } catch (RestClientException e) {
            log.error("调用 RagAgent v2 推荐资源失败，回退到 v1。", e);
            return attachResourceAdaptation(recommendResourcesV1(topic, level), adaptiveContext);
        }
    }

    /**
     * 调用 FastAPI DetailPlanAgent，对某一天的任务进行细化。
     */
    public List<String> refineDayTasks(String title,
                                       List<String> currentTasks,
                                       String goalText,
                                       Integer hoursPerDay,
                                       String level) {
        String url = "/api/plan/day/refine";

        AgentDetailPlanRefineRequest payload = new AgentDetailPlanRefineRequest();
        payload.setTitle(title);
        payload.setCurrentTasks(currentTasks);
        payload.setGoalText(goalText);
        payload.setHoursPerDay(hoursPerDay);
        payload.setLevel(level);

        try {
            AgentDetailPlanRefineResponse response = postJson(
                    AgentOperation.PLAN,
                    url,
                    payload,
                    AgentDetailPlanRefineResponse.class
            );
            if (response == null || response.getTasks() == null) {
                log.warn("DetailPlanAgent 返回空响应");
                return currentTasks;
            }
            return response.getTasks();
        } catch (RestClientException e) {
            log.error("调用 DetailPlanAgent 细化当日任务失败", e);
            return currentTasks;
        }
    }

    public List<ExerciseQuestionDto> generateExercises(String title,
                                                       String goalText,
                                                       String level) {
        return generateExercises(title, goalText, level, null, null, null, null, 2);
    }

    /**
     * 调用 FastAPI TutorAgent v2，根据更丰富的上下文生成练习题。
     */
    public List<ExerciseQuestionDto> generateExercises(String title,
                                                       String goalText,
                                                       String level,
                                                       String phaseTitle,
                                                       Integer weekIndex,
                                                       Integer dayIndex,
                                                       String taskType,
                                                       Integer questionCount) {
        return generateExercises(title, goalText, level, phaseTitle, weekIndex, dayIndex,
                taskType, questionCount, null);
    }

    public List<ExerciseQuestionDto> generateExercises(String title,
                                                       String goalText,
                                                       String level,
                                                       String phaseTitle,
                                                       Integer weekIndex,
                                                       Integer dayIndex,
                                                       String taskType,
                                                       Integer questionCount,
                                                       AdaptationMetadataDto adaptiveContext) {
        String url = "/api/v2/tutor/exercise";
        AgentTutorGenerateRequest payload = buildTutorGenerateRequest(
                title,
                goalText,
                level,
                phaseTitle,
                weekIndex,
                dayIndex,
                taskType,
                questionCount != null ? questionCount : 2
        );
        payload.setAdaptiveContext(adaptiveContext);

        try {
            AgentTutorSessionResponse response = postJson(
                    AgentOperation.TUTOR,
                    url,
                    payload,
                    AgentTutorSessionResponse.class
            );
            if (response == null || response.getQuestions() == null) {
                log.warn("TutorAgent v2 返回空响应");
                return attachExerciseAdaptation(generateExercisesV1(title, goalText, level), adaptiveContext);
            }

            return attachExerciseAdaptation(response.getQuestions().stream()
                    .map(this::mapToExerciseQuestion)
                    .filter(dto -> dto.getQuestion() != null && !dto.getQuestion().isBlank())
                    .toList(), adaptiveContext);
        } catch (RestClientException e) {
            log.error("调用 TutorAgent v2 生成练习题失败，回退到 v1。", e);
            return attachExerciseAdaptation(generateExercisesV1(title, goalText, level), adaptiveContext);
        }
    }

    /**
     * 调用 FastAPI TutorAgent v2，对学生作答进行评估。
     */
    public ExerciseEvaluateResponseDto evaluateExercise(String title,
                                                        String goalText,
                                                        String level,
                                                        String phaseTitle,
                                                        String question,
                                                        String referenceAnswer,
                                                        String userAnswer) {
        String url = "/api/v2/tutor/evaluate";

        AgentTutorEvaluateRequest payload = new AgentTutorEvaluateRequest();
        payload.setTitle(title);
        payload.setGoalText(goalText);
        payload.setLevel(level);
        payload.setPhaseTitle(phaseTitle);
        payload.setQuestion(question);
        payload.setReferenceAnswer(referenceAnswer);
        payload.setUserAnswer(userAnswer);

        try {
            AgentTutorEvaluateResponse response = postJson(
                    AgentOperation.TUTOR,
                    url,
                    payload,
                    AgentTutorEvaluateResponse.class
            );
            if (response == null || response.getAttempt() == null) {
                log.warn("TutorAgent v2 评估返回空响应");
                return buildFallbackEvaluation(question, referenceAnswer, userAnswer);
            }
            return mapToExerciseEvaluation(response.getAttempt());
        } catch (RestClientException e) {
            log.error("调用 TutorAgent v2 评估练习失败，使用本地兜底评估。", e);
            return buildFallbackEvaluation(question, referenceAnswer, userAnswer);
        }
    }

    /**
     * 查询 FastAPI Agent 平台中的多 Agent 调用日志。
     */
    public List<AgentCallLogDto> getAgentLogs(String traceId, Integer limit) {
        int realLimit = (limit == null || limit <= 0) ? 50 : limit;
        StringBuilder url = new StringBuilder("/api/agent/logs?limit=")
                .append(realLimit);
        if (traceId != null && !traceId.isBlank()) {
            url.append("&trace_id=").append(traceId);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = agentHttpClient.get(AgentOperation.ADMIN, url.toString(), Map.class);
            if (response == null) {
                log.warn("获取 Agent 调用日志返回空响应");
                return List.of();
            }
            Object itemsObj = response.get("items");
            if (!(itemsObj instanceof List<?> list)) {
                log.warn("Agent 调用日志响应中 items 字段不是列表");
                return List.of();
            }

            return list.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> {
                        Map<?, ?> map = (Map<?, ?>) item;
                        AgentCallLogDto dto = new AgentCallLogDto();
                        Object id = map.get("id");
                        if (id instanceof Number number) {
                            dto.setId(number.longValue());
                        }
                        Object t = map.get("trace_id");
                        Object agentName = map.get("agent_name");
                        Object req = map.get("request_payload");
                        Object resp = map.get("response_payload");
                        Object model = map.get("model_name");
                        Object dur = map.get("duration_ms");
                        Object createdAt = map.get("created_at");

                        dto.setTraceId(t != null ? t.toString() : null);
                        dto.setAgentName(agentName != null ? agentName.toString() : null);
                        dto.setRequestPayload(req != null ? req.toString() : null);
                        dto.setResponsePayload(resp != null ? resp.toString() : null);
                        dto.setModelName(model != null ? model.toString() : null);
                        if (dur instanceof Number numberDur) {
                            dto.setDurationMs(numberDur.intValue());
                        }
                        if (createdAt != null) {
                            try {
                                dto.setCreatedAt(OffsetDateTime.parse(createdAt.toString()));
                            } catch (Exception e) {
                                dto.setCreatedAt(null);
                            }
                        }
                        return dto;
                    })
                    .toList();
        } catch (RestClientException e) {
            log.error("调用 FastAPI 获取 Agent 调用日志失败", e);
            return List.of();
        }
    }

    private PlanResponse buildFallbackPlan(GoalRequest request) {
        PlanResponse response = new PlanResponse();
        response.setPlanId(UUID.randomUUID().toString());
        response.setTitle("本地示例学习计划（AI 平台暂不可用时的回退结果）");

        LocalDate today = LocalDate.now();
        response.setStartDate(today);
        response.setEndDate(today.plusDays(2));

        Integer estimatedMinutes = request.getHoursPerDay() != null ? request.getHoursPerDay() * 60 : null;

        PlanDayDto day1 = new PlanDayDto();
        day1.setDayIndex(1);
        day1.setWeekIndex(1);
        day1.setTaskType("learn");
        day1.setEstimatedMinutes(estimatedMinutes);
        day1.setDate(today);
        day1.setTitle("了解基础概念与环境搭建");
        day1.setTasks(List.of(
                "阅读：什么是 Java / SpringBoot / 多 Agent 学习系统",
                "完成：安装 JDK 和一个 IDE（例如 IntelliJ IDEA / VS Code）",
                "思考：自己可以每天固定什么时间学习"
        ));
        day1.setStatus("not_started");

        PlanDayDto day2 = new PlanDayDto();
        day2.setDayIndex(2);
        day2.setWeekIndex(1);
        day2.setTaskType("practice");
        day2.setEstimatedMinutes(estimatedMinutes);
        day2.setDate(today.plusDays(1));
        day2.setTitle("Java 语法基础预热");
        day2.setTasks(List.of(
                "了解：变量、数据类型、if 条件、for 循环",
                "尝试：写一个简单的 Console 程序，输出自己的学习目标"
        ));
        day2.setStatus("not_started");

        PlanDayDto day3 = new PlanDayDto();
        day3.setDayIndex(3);
        day3.setWeekIndex(1);
        day3.setTaskType("review");
        day3.setEstimatedMinutes(estimatedMinutes);
        day3.setDate(today.plusDays(2));
        day3.setTitle("面向对象初体验");
        day3.setTasks(List.of(
                "理解：类、对象的基本概念",
                "思考：如果用一个类表示“学习计划”，它大概需要哪些属性"
        ));
        day3.setStatus("not_started");

        response.setDays(List.of(day1, day2, day3));
        return response;
    }

    private PlanResponse parsePlanResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawResponse, PlanResponse.class);
        } catch (JsonProcessingException e) {
            log.error("解析 AI 计划响应失败，responseLength={}", rawResponse.length(), e);
            return null;
        }
    }

    private List<ResourceItemDto> attachResourceAdaptation(List<ResourceItemDto> resources,
                                                            AdaptationMetadataDto adaptation) {
        resources.forEach(resource -> {
            resource.setAdaptation(adaptation);
            if (adaptation != null && Boolean.TRUE.equals(adaptation.getApplied())) {
                String explanation = "根据掌握度优先匹配 " + adaptation.getTargetDifficulty() + " 难度";
                resource.setReason(resource.getReason() == null || resource.getReason().isBlank()
                        ? explanation : explanation + "；" + resource.getReason());
            }
        });
        return resources;
    }
    private List<ResourceItemDto> recommendResourcesV1(String topic, String level) {
        String url = "/api/rag/resources";
        AgentResourceQueryRequest payload = buildResourceQueryRequest(topic, level, null, null, null, null, null, null, null, null);

        try {
            AgentResourceRecommendResponse response = postJson(
                    AgentOperation.RAG,
                    url,
                    payload,
                    AgentResourceRecommendResponse.class
            );
            if (response == null || response.getResources() == null) {
                log.warn("RagAgent v1 返回空响应");
                return List.of();
            }

            return response.getResources().stream()
                    .map(item -> mapToResourceItem(item, response.getQuerySummary()))
                    .toList();
        } catch (RestClientException e) {
            log.error("调用 RagAgent v1 推荐资源失败", e);
            return List.of();
        }
    }

    private ResourceItemDto mapToResourceItem(AgentResourceItem item, String querySummary) {
        ResourceItemDto dto = new ResourceItemDto();
        dto.setId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setUrl(item.getUrl());
        dto.setLevel(item.getLevel());
        dto.setDomain(item.getDomain());
        dto.setDurationMinutes(item.getDurationMinutes());
        dto.setTags(joinList(item.getTags()));
        dto.setStatus(item.getStatus());
        dto.setReason(item.getReason());
        dto.setScore(item.getScore());
        dto.setMatchedTerms(joinList(item.getMatchedTerms()));
        dto.setRetrievalChannels(joinList(item.getRetrievalChannels()));
        dto.setEvidence(item.getEvidence() == null
                ? List.of()
                : item.getEvidence().stream().map(this::mapResourceEvidence).toList());
        dto.setConfidence(item.getConfidence());
        dto.setEvidenceStatus(item.getEvidenceStatus());
        dto.setSource(item.getSource());
        dto.setQuerySummary(querySummary);
        return dto;
    }

    private ResourceEvidenceDto mapResourceEvidence(AgentResourceEvidence item) {
        ResourceEvidenceDto dto = new ResourceEvidenceDto();
        dto.setChunkId(item.getChunkId());
        dto.setExcerpt(item.getExcerpt());
        dto.setSourceUrl(item.getSourceUrl());
        dto.setContentHash(item.getContentHash());
        dto.setRetrievalChannels(item.getRetrievalChannels() == null
                ? List.of()
                : List.copyOf(item.getRetrievalChannels()));
        return dto;
    }

    private List<ExerciseQuestionDto> attachExerciseAdaptation(List<ExerciseQuestionDto> questions,
                                                                AdaptationMetadataDto adaptation) {
        questions.forEach(question -> question.setAdaptation(adaptation));
        return questions;
    }
    private List<ExerciseQuestionDto> generateExercisesV1(String title,
                                                          String goalText,
                                                          String level) {
        String url = "/api/tutor/exercise";
        AgentTutorGenerateRequest payload = buildTutorGenerateRequest(title, goalText, level, null, null, null, null, null);

        try {
            AgentTutorExerciseResponse response = postJson(
                    AgentOperation.TUTOR,
                    url,
                    payload,
                    AgentTutorExerciseResponse.class
            );
            if (response == null || response.getQuestions() == null) {
                log.warn("TutorAgent v1 返回空响应");
                return List.of();
            }

            return response.getQuestions().stream()
                    .map(this::mapToExerciseQuestion)
                    .filter(dto -> dto.getQuestion() != null && !dto.getQuestion().isBlank())
                    .toList();
        } catch (RestClientException e) {
            log.error("调用 TutorAgent v1 生成练习题失败", e);
            return List.of();
        }
    }

    private ExerciseQuestionDto mapToExerciseQuestion(AgentTutorQuestion item) {
        ExerciseQuestionDto dto = new ExerciseQuestionDto();
        dto.setQuestion(item.getQuestion());
        dto.setAnswer(item.getAnswer());
        dto.setExplanation(item.getExplanation());
        dto.setDifficulty(item.getDifficulty());
        dto.setSkillFocus(item.getSkillFocus());
        return dto;
    }

    private ExerciseEvaluateResponseDto mapToExerciseEvaluation(AgentTutorAttempt attempt) {
        ExerciseEvaluateResponseDto dto = new ExerciseEvaluateResponseDto();
        dto.setQuestion(attempt.getQuestion());
        dto.setReferenceAnswer(attempt.getReferenceAnswer());
        dto.setUserAnswer(attempt.getUserAnswer());
        dto.setScore(attempt.getScore());
        dto.setMistakeType(attempt.getMistakeType());
        dto.setFeedback(attempt.getFeedback());
        dto.setNextRecommendation(attempt.getNextRecommendation());
        return dto;
    }

    private ExerciseEvaluateResponseDto buildFallbackEvaluation(String question,
                                                                String referenceAnswer,
                                                                String userAnswer) {
        ExerciseEvaluateResponseDto dto = new ExerciseEvaluateResponseDto();
        dto.setQuestion(question);
        dto.setReferenceAnswer(referenceAnswer);
        dto.setUserAnswer(userAnswer);

        String normalizedReference = normalizeText(referenceAnswer);
        String normalizedUser = normalizeText(userAnswer);
        int overlapScore = calculateCharacterOverlapScore(normalizedReference, normalizedUser);

        if (!normalizedReference.isBlank() && normalizedReference.equals(normalizedUser)) {
            dto.setScore(95);
            dto.setMistakeType("minor_gap");
            dto.setFeedback("答案和参考答案高度一致，可以继续进入下一题。\n如需更稳固，可以再补一句自己的理解。");
            dto.setNextRecommendation("尝试用自己的话复述核心概念，确认不是机械记忆。");
            return dto;
        }

        if (overlapScore >= 70) {
            dto.setScore(78);
            dto.setMistakeType("partial_understanding");
            dto.setFeedback("答案已经覆盖了主要思路，但还缺少关键细节或表达不够完整。\n建议对照参考答案补齐核心点。");
            dto.setNextRecommendation("把参考答案拆成 2 到 3 个关键点，再按关键点重写一次。");
            return dto;
        }

        if (overlapScore >= 40) {
            dto.setScore(58);
            dto.setMistakeType("concept_gap");
            dto.setFeedback("答案体现出部分理解，但核心概念之间的关系还不够清晰。\n先回看当天主题中的定义、用途和一个最小示例。");
            dto.setNextRecommendation("重新阅读相关任务或资源后，再用“概念 + 作用 + 例子”的格式回答一次。");
            return dto;
        }

        dto.setScore(35);
        dto.setMistakeType("concept_gap");
        dto.setFeedback("当前答案和参考答案偏差较大，说明这部分还没有真正掌握。\n建议先回到基础材料，重新整理最核心的知识点。");
        dto.setNextRecommendation("先看推荐资源中的入门内容，再完成一道相似的基础题。");
        return dto;
    }

    private AgentResourceQueryRequest buildResourceQueryRequest(String topic,
                                                                String level,
                                                                String domain,
                                                                String goalText,
                                                                List<String> taskTexts,
                                                                Integer estimatedMinutes,
                                                                String phaseTitle,
                                                                String weekTheme,
                                                                String taskType,
                                                                Integer topK) {
        AgentResourceQueryRequest payload = new AgentResourceQueryRequest();
        payload.setTopic(topic);
        payload.setLevel(level);
        payload.setDomain(domain);
        payload.setGoalText(goalText);
        payload.setTaskTexts(taskTexts);
        payload.setEstimatedMinutes(estimatedMinutes);
        payload.setPhaseTitle(phaseTitle);
        payload.setWeekTheme(weekTheme);
        payload.setTaskType(taskType);
        payload.setTopK(topK);
        return payload;
    }

    private String inferLearningDomain(String topic, String goalText, List<String> taskTexts) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, topic);
        appendIfPresent(text, goalText);
        if (taskTexts != null) {
            for (String task : taskTexts) {
                appendIfPresent(text, task);
            }
        }

        String normalized = text.toString().toLowerCase();
        if (normalized.isBlank()) {
            return null;
        }
        if (containsAny(normalized, "英语", "cet", "cet4", "cet6", "四级", "六级", "单词", "阅读理解", "写作", "english")) {
            return "english";
        }
        if (containsAny(normalized, "java", "spring", "jvm", "面向对象", "stream")) {
            return "java";
        }
        if (containsAny(normalized, "python", "pandas", "numpy", "爬虫")) {
            return "python";
        }
        if (containsAny(normalized, "mysql", "postgres", "sql", "数据库", "mybatis")) {
            return "database";
        }
        if (containsAny(normalized, "高数", "数学", "线代", "概率", "math")) {
            return "math";
        }
        if (containsAny(normalized, "linux", "shell", "docker", "运维")) {
            return "devops";
        }
        if (containsAny(normalized, "前端", "vue", "react", "javascript", "css", "html")) {
            return "frontend";
        }
        return null;
    }

    private void appendIfPresent(StringBuilder sb, String text) {
        if (text != null && !text.isBlank()) {
            sb.append(text).append(' ');
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private AgentTutorGenerateRequest buildTutorGenerateRequest(String title,
                                                                String goalText,
                                                                String level,
                                                                String phaseTitle,
                                                                Integer weekIndex,
                                                                Integer dayIndex,
                                                                String taskType,
                                                                Integer questionCount) {
        AgentTutorGenerateRequest payload = new AgentTutorGenerateRequest();
        payload.setTitle(title);
        payload.setGoalText(goalText);
        payload.setLevel(level);
        payload.setPhaseTitle(phaseTitle);
        payload.setWeekIndex(weekIndex);
        payload.setDayIndex(dayIndex);
        payload.setTaskType(taskType);
        payload.setQuestionCount(questionCount);
        return payload;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "")
                .replaceAll("[，。；：,.!?！？、]", "")
                .trim()
                .toLowerCase();
    }

    private int calculateCharacterOverlapScore(String reference, String userAnswer) {
        if (reference == null || reference.isBlank() || userAnswer == null || userAnswer.isBlank()) {
            return 0;
        }

        Set<Integer> referenceChars = new HashSet<>();
        reference.codePoints().forEach(referenceChars::add);

        Set<Integer> userChars = new HashSet<>();
        userAnswer.codePoints().forEach(userChars::add);

        if (referenceChars.isEmpty()) {
            return 0;
        }

        long matched = referenceChars.stream().filter(userChars::contains).count();
        return (int) Math.round(matched * 100.0 / referenceChars.size());
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private <T> T postJson(
            AgentOperation operation,
            String url,
            Object payload,
            Class<T> responseType
    ) throws RestClientException {
        return agentHttpClient.postJson(operation, url, payload, responseType);
    }
}

