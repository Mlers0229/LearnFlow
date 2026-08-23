package com.learnflow.dto.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResourceItemContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesBackwardCompatibleRetrievalChannelProvenance() throws Exception {
        AgentResourceItem item = objectMapper.readValue(
                """
                {
                  "id": 42,
                  "title": "Spring",
                  "url": "https://example.com/spring",
                  "retrieval_channels": ["dense", "sparse"]
                }
                """,
                AgentResourceItem.class
        );

        assertThat(item.getId()).isEqualTo(42L);
        assertThat(item.getRetrievalChannels()).containsExactly("dense", "sparse");
    }

    @Test
    void missingRetrievalChannelsKeepsOldAgentResponsesCompatible() throws Exception {
        AgentResourceItem item = objectMapper.readValue(
                """
                {"id": 7, "title": "Legacy", "url": "https://example.com/legacy"}
                """,
                AgentResourceItem.class
        );

        assertThat(item.getRetrievalChannels()).isEmpty();
    }
}
