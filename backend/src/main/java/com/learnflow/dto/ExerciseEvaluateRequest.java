package com.learnflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 调用 TutorAgent v2 评估学生作答时使用的请求体。
 */
public class ExerciseEvaluateRequest {

    @NotBlank
    @Size(max = 4000)
    private String question;

    @NotBlank
    @Size(max = 4000)
    private String referenceAnswer;

    @NotBlank
    @Size(max = 8000)
    private String userAnswer;

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
}
