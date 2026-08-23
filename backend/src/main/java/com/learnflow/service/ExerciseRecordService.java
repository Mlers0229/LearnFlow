package com.learnflow.service;

import com.learnflow.dto.ExerciseRecordCreateRequest;
import com.learnflow.dto.ExerciseRecordItemDto;
import com.learnflow.dto.ExerciseRecordListResponse;
import com.learnflow.dto.ExerciseReviewSummaryDto;
import com.learnflow.entity.ExerciseRecord;
import com.learnflow.entity.StudyPlan;
import com.learnflow.entity.StudyPlanDay;
import com.learnflow.entity.User;
import com.learnflow.repository.ExerciseRecordRepository;
import com.learnflow.repository.StudyPlanDayRepository;
import com.learnflow.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 练习记录相关的业务逻辑。
 */
@Service
public class ExerciseRecordService {

    private final ExerciseRecordRepository exerciseRecordRepository;
    private final StudyPlanDayRepository studyPlanDayRepository;
    private final UserRepository userRepository;
    private final MasteryService masteryService;

    public ExerciseRecordService(ExerciseRecordRepository exerciseRecordRepository,
                                 StudyPlanDayRepository studyPlanDayRepository,
                                 UserRepository userRepository,
                                 MasteryService masteryService) {
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.studyPlanDayRepository = studyPlanDayRepository;
        this.userRepository = userRepository;
        this.masteryService = masteryService;
    }

    /**
     * 保存某一天下的一条练习记录。
     *
     * @param dayId   学习计划中的某一天 ID
     * @param request 包含题干、参考答案、AI 评测和用户作答的请求体
     * @throws IllegalArgumentException 当 dayId 无效时
     */
    @Transactional
    public void saveRecord(Long dayId, Long userId, ExerciseRecordCreateRequest request) {
        Optional<StudyPlanDay> dayOpt = studyPlanDayRepository.findById(dayId);
        if (dayOpt.isEmpty()) {
            throw new IllegalArgumentException("无效的 dayId：" + dayId);
        }

        StudyPlanDay day = dayOpt.get();
        if (day.getPlan() == null || userId == null || !userId.equals(day.getPlan().getUserId())) {
            throw new IllegalArgumentException("无效的 dayId：" + dayId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        ExerciseRecord record = new ExerciseRecord();
        record.setPlanDay(day);
        record.setUser(user);

        record.setQuestion(request.getQuestion());
        record.setAnswerCorrect(request.getAnswer());
        record.setExplanation(request.getExplanation());
        record.setDifficulty(request.getDifficulty());
        record.setSkillFocus(request.getSkillFocus());
        record.setAnswerUser(request.getUserAnswer());
        record.setScore(request.getAiScore());
        record.setMistakeType(request.getAiMistakeType());
        record.setFeedback(request.getAiFeedback());
        record.setNextRecommendation(request.getAiNextRecommendation());
        record.setIsCorrect(resolveIsCorrect(request.getAiScore()));

        ExerciseRecord saved = exerciseRecordRepository.save(record);
        masteryService.recordExerciseAnswered(userId, saved);
    }

    /**
     * 查询某个用户的练习记录列表，用于练习回顾页。
     */
    @Transactional(readOnly = true)
    public ExerciseRecordListResponse listRecords(Long userId, Long planId, Long dayId, Integer limit) {
        int realLimit = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        PageRequest pageRequest = PageRequest.of(0, realLimit);

        Page<ExerciseRecord> page;
        if (dayId != null) {
            page = exerciseRecordRepository.findByUser_IdAndPlanDay_IdOrderByCreatedAtDesc(userId, dayId, pageRequest);
        } else if (planId != null) {
            page = exerciseRecordRepository.findByUser_IdAndPlanDay_Plan_IdOrderByCreatedAtDesc(userId, planId, pageRequest);
        } else {
            page = exerciseRecordRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageRequest);
        }

        List<ExerciseRecordItemDto> items = page.getContent().stream()
                .map(this::mapToItemDto)
                .toList();

        ExerciseRecordListResponse response = new ExerciseRecordListResponse();
        response.setItems(items);
        response.setSummary(buildSummary(items));
        return response;
    }

    /**
     * 删除单条练习记录，仅允许删除当前用户自己的记录。
     */
    @Transactional
    public void deleteRecord(Long recordId, Long userId) {
        ExerciseRecord record = exerciseRecordRepository.findByIdAndUser_Id(recordId, userId)
                .orElseThrow(() -> new IllegalArgumentException("练习记录不存在，或无权删除，id=" + recordId));
        masteryService.reverseExerciseAnswered(userId, record);
        exerciseRecordRepository.delete(record);
    }

    /**
     * 清空某个学习日下当前用户的练习记录。
     *
     * @return 实际删除的记录数量
     */
    @Transactional
    public long deleteRecordsByDay(Long dayId, Long userId) {
        List<ExerciseRecord> records = exerciseRecordRepository.findAllByUser_IdAndPlanDay_Id(userId, dayId);
        records.forEach(record -> masteryService.reverseExerciseAnswered(userId, record));
        exerciseRecordRepository.deleteAll(records);
        return records.size();
    }

    @Transactional
    public void markReviewed(Long recordId, Long userId) {
        ExerciseRecord record = exerciseRecordRepository.findByIdAndUser_Id(recordId, userId)
                .orElseThrow(() -> new IllegalArgumentException("练习记录不存在，或无权访问，id=" + recordId));
        masteryService.recordExerciseReviewed(userId, record);
    }

    private ExerciseRecordItemDto mapToItemDto(ExerciseRecord record) {
        ExerciseRecordItemDto dto = new ExerciseRecordItemDto();
        dto.setId(record.getId());
        dto.setQuestion(record.getQuestion());
        dto.setReferenceAnswer(record.getAnswerCorrect());
        dto.setExplanation(record.getExplanation());
        dto.setDifficulty(record.getDifficulty());
        dto.setSkillFocus(record.getSkillFocus());
        dto.setUserAnswer(record.getAnswerUser());
        dto.setAiScore(record.getScore());
        dto.setAiMistakeType(record.getMistakeType());
        dto.setAiFeedback(record.getFeedback());
        dto.setAiNextRecommendation(record.getNextRecommendation());
        dto.setIsCorrect(record.getIsCorrect());
        dto.setCreatedAt(record.getCreatedAt());

        StudyPlanDay planDay = record.getPlanDay();
        if (planDay != null) {
            dto.setDayId(planDay.getId());
            dto.setDayDate(planDay.getDate());
            dto.setDayTitle(planDay.getTitle());

            StudyPlan plan = planDay.getPlan();
            if (plan != null) {
                dto.setPlanId(plan.getId());
                dto.setPlanTitle(plan.getTitle());
            }
        }
        return dto;
    }

    private ExerciseReviewSummaryDto buildSummary(List<ExerciseRecordItemDto> items) {
        ExerciseReviewSummaryDto summary = new ExerciseReviewSummaryDto();
        summary.setTotalRecords(items.size());

        List<Integer> scores = items.stream()
                .map(ExerciseRecordItemDto::getAiScore)
                .filter(score -> score != null)
                .toList();

        summary.setScoredRecords(scores.size());
        if (!scores.isEmpty()) {
            double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            summary.setAverageScore(Math.round(avg * 10.0) / 10.0);
            summary.setHighestScore(scores.stream().max(Comparator.naturalOrder()).orElse(null));
            summary.setLatestScore(scores.get(0));
        }

        int masteredCount = (int) items.stream()
                .filter(item -> item.getAiScore() != null && item.getAiScore() >= 85)
                .count();
        summary.setMasteredCount(masteredCount);

        int needsReviewCount = (int) items.stream()
                .filter(item -> item.getAiScore() != null)
                .filter(item -> item.getAiScore() < 60 || "concept_gap".equals(item.getAiMistakeType()))
                .count();
        summary.setNeedsReviewCount(needsReviewCount);
        return summary;
    }

    private Boolean resolveIsCorrect(Integer aiScore) {
        if (aiScore == null) {
            return null;
        }
        return aiScore >= 85;
    }
}
