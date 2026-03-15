package com.learnflow.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 练习记录表（exercise_record）。
 *
 * 用于沉淀学生作答与 AI 评测结果，支撑练习回顾页与后续学习诊断。
 */
@Entity
@Table(name = "exercise_record")
public class ExerciseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 对应哪一天的学习计划。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_day_id")
    private StudyPlanDay planDay;

    /**
     * 哪个用户的练习记录（可为空，避免与未登录场景强绑定）。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * 题干（来自 TutorAgent 返回的 question）。
     */
    @Column(columnDefinition = "text", nullable = false)
    private String question;

    /**
     * 参考答案（来自 TutorAgent 返回的 answer）。
     */
    @Column(name = "answer_correct", columnDefinition = "text")
    private String answerCorrect;

    /**
     * 题目原始讲解。
     */
    @Column(columnDefinition = "text")
    private String explanation;

    /**
     * 题目难度。
     */
    private String difficulty;

    /**
     * 题目考察点。
     */
    private String skillFocus;

    /**
     * 用户实际写下的答案。
     */
    @Column(name = "answer_user", columnDefinition = "text")
    private String answerUser;

    /**
     * 是否答对（根据 AI 评分做粗略映射，允许为空）。
     */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    /**
     * AI 反馈内容。
     */
    @Column(columnDefinition = "text")
    private String feedback;

    /**
     * AI 评分。
     */
    private Integer score;

    /**
     * AI 识别出的错误类型。
     */
    private String mistakeType;

    /**
     * AI 给出的下一步建议。
     */
    @Column(columnDefinition = "text")
    private String nextRecommendation;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StudyPlanDay getPlanDay() {
        return planDay;
    }

    public void setPlanDay(StudyPlanDay planDay) {
        this.planDay = planDay;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswerCorrect() {
        return answerCorrect;
    }

    public void setAnswerCorrect(String answerCorrect) {
        this.answerCorrect = answerCorrect;
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

    public String getAnswerUser() {
        return answerUser;
    }

    public void setAnswerUser(String answerUser) {
        this.answerUser = answerUser;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getMistakeType() {
        return mistakeType;
    }

    public void setMistakeType(String mistakeType) {
        this.mistakeType = mistakeType;
    }

    public String getNextRecommendation() {
        return nextRecommendation;
    }

    public void setNextRecommendation(String nextRecommendation) {
        this.nextRecommendation = nextRecommendation;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
