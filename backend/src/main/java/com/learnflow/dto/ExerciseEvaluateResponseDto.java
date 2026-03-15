package com.learnflow.dto;

/**
 * TutorAgent v2 对学生作答的评估结果。
 */
public class ExerciseEvaluateResponseDto {

    private String question;

    private String referenceAnswer;

    private String userAnswer;

    private Integer score;

    private String mistakeType;

    private String feedback;

    private String nextRecommendation;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public void setReferenceAnswer(String referenceAnswer) {
        this.referenceAnswer = referenceAnswer;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
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

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getNextRecommendation() {
        return nextRecommendation;
    }

    public void setNextRecommendation(String nextRecommendation) {
        this.nextRecommendation = nextRecommendation;
    }
}
