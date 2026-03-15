package com.learnflow.dto.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentTutorSessionResponse {

    private String mode;
    private List<AgentTutorQuestion> questions = new ArrayList<>();
    private String learningTip;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<AgentTutorQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<AgentTutorQuestion> questions) {
        this.questions = questions;
    }

    public String getLearningTip() {
        return learningTip;
    }

    public void setLearningTip(String learningTip) {
        this.learningTip = learningTip;
    }
}
