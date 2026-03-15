package com.learnflow.dto.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentDetailPlanRefineResponse {

    private List<String> tasks = new ArrayList<>();

    public List<String> getTasks() {
        return tasks;
    }

    public void setTasks(List<String> tasks) {
        this.tasks = tasks;
    }
}
