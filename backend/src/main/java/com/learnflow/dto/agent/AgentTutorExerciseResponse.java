package com.learnflow.dto.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentTutorExerciseResponse {

    private List<AgentTutorQuestion> questions = new ArrayList<>();

    public List<AgentTutorQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<AgentTutorQuestion> questions) {
        this.questions = questions;
    }
}
