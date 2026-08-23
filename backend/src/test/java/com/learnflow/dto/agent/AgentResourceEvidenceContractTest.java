package com.learnflow.dto.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResourceEvidenceContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesVerifiableEvidenceAndConfidence() throws Exception {
        AgentResourceItem item = objectMapper.readValue(
                """
                {
                  "id": 42,
                  "title": "Spring",
                  "url": "https://example.com/spring",
                  "confidence": 0.91,
                  "evidence_status": "verified",
                  "evidence": [{
                    "chunk_id": "123e4567-e89b-12d3-a456-426614174000",
                    "excerpt": "Spring evidence",
                    "source_url": "https://example.com/spring",
                    "content_hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "retrieval_channels": ["dense", "sparse"]
                  }]
                }
                """,
                AgentResourceItem.class
        );

        assertThat(item.getConfidence()).isEqualTo(0.91);
        assertThat(item.getEvidenceStatus()).isEqualTo("verified");
        assertThat(item.getEvidence()).hasSize(1);
        assertThat(item.getEvidence().get(0).getExcerpt()).isEqualTo("Spring evidence");
        assertThat(item.getEvidence().get(0).getRetrievalChannels())
                .containsExactly("dense", "sparse");
    }

    @Test
    void oldResponsesKeepEvidenceCollectionEmpty() throws Exception {
        AgentResourceItem item = objectMapper.readValue(
                """
                {"id": 7, "title": "Legacy", "url": "https://example.com"}
                """,
                AgentResourceItem.class
        );

        assertThat(item.getEvidence()).isEmpty();
        assertThat(item.getConfidence()).isNull();
        assertThat(item.getEvidenceStatus()).isEqualTo("unverified");
    }
}
