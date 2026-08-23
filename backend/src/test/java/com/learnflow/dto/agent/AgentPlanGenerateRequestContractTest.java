package com.learnflow.dto.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlanGenerateRequestContractTest {

    @Test
    void serializesWorkflowIdUsingSnakeCase() throws Exception {
        AgentPlanGenerateRequest request = new AgentPlanGenerateRequest();
        request.setGoalText("Learn Java");
        request.setWorkflowId("40e1463d-da27-4b03-9277-5123813dd59e");

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).contains("\"workflow_id\":\"40e1463d-da27-4b03-9277-5123813dd59e\"");
        assertThat(json).doesNotContain("workflowId");
    }
}
