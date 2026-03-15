package com.learnflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 前端提交练习记录时使用的请求体。
 */
public class ExerciseRecordCreateRequest {

    /**
     * 当前登录用户 ID，可空。
     */
    private Long userId;

    /**
     * 题干（来自 TutorAgent 返回的 question）。
     */
    @NotBlank
    @Size(max = 4000)
    private String question;

    /**
     * 参考答案（来自 TutorAgent 返回的 answer）。
     */
    @NotNull
    @Size(max = 4000)
    private String answer;

    /**
     * 题目讲解，可空。
     */
    @Size(max = 4000)
    private String explanation;

    /**
     * 题目难度，可空。
     */
    @Size(max = 100)
    private String difficulty;

    /**
     * 题目考察点，可空。
     */
    @Size(max = 255)
    private String skillFocus;

    /**
     * 用户实际作答内容。
     */
    @NotBlank
    @Size(max = 8000)
    private String userAnswer;

    /**
     * AI 评分，可空。
     */
    @Min(0)
    @Max(100)
    private Integer aiScore;

    /**
     * AI 判断的错误类型，可空。
     */
    @Size(max = 100)
    private String aiMistakeType;

    /**
     * AI 反馈，可空。
     */
    @Size(max = 4000)
    private String aiFeedback;

    /**
     * AI 下一步建议，可空。
     */
    @Size(max = 4000)
    private String aiNextRecommendation;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getSkillFocus() {
        return skillFocus;
    }

    public void setSkillFocus(String skillFocus) {
        this.skillFocus = skillFocus;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public Integer getAiScore() {
        return aiScore;
    }

    public void setAiScore(Integer aiScore) {
        this.aiScore = aiScore;
    }

    public String getAiMistakeType() {
        return aiMistakeType;
    }

    public void setAiMistakeType(String aiMistakeType) {
        this.aiMistakeType = aiMistakeType;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public void setAiFeedback(String aiFeedback) {
        this.aiFeedback = aiFeedback;
    }

    public String getAiNextRecommendation() {
        return aiNextRecommendation;
    }

    public void setAiNextRecommendation(String aiNextRecommendation) {
        this.aiNextRecommendation = aiNextRecommendation;
    }
}
