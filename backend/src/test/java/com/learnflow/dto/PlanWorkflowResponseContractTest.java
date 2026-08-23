package com.learnflow.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanWorkflowResponseContractTest {

    @Test
    void parsesStatefulAgentWorkflowMetadata() throws Exception {
        String json = """
                {
                  "plan_id": "draft",
                  "title": "Plan",
                  "start_date": "2026-08-22",
                  "end_date": "2026-08-22",
                  "days": [],
                  "workflow_id": "40e1463d-da27-4b03-9277-5123813dd59e",
                  "workflow_status": "READY_TO_SAVE",
                  "completed_node": "SAVE",
                  "state_schema_version": 1
                }
                """;

        PlanResponse response = new ObjectMapper().findAndRegisterModules()
                .readValue(json, PlanResponse.class);

        assertThat(response.getWorkflowId()).isEqualTo("40e1463d-da27-4b03-9277-5123813dd59e");
        assertThat(response.getWorkflowStatus()).isEqualTo("READY_TO_SAVE");
        assertThat(response.getCompletedNode()).isEqualTo("SAVE");
        assertThat(response.getStateSchemaVersion()).isEqualTo(1);
    }
}
