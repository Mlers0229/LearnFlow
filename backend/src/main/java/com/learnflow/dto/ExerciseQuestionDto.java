package com.learnflow.dto;

/**
 * TutorAgent 生成的一道练习题 DTO。
 *
 * 前端展示时：
 * - question：题干（展示给学生）
 * - answer：参考答案（默认可折叠、不必一开始就展示）
 * - explanation：简要讲解，可为空
 */
public class ExerciseQuestionDto {

    private String question;

    private String answer;

    private String explanation;

    private String difficulty;

    private String skillFocus;

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
}
