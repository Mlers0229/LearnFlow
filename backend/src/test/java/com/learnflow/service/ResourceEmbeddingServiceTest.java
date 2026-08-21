package com.learnflow.service;

import com.learnflow.config.LearnFlowEmbeddingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ResourceEmbeddingServiceTest {

    @Test
    void supersededTaskCannotWriteUsingTheNewConfiguredVersion() {
        LearnFlowEmbeddingProperties properties = new LearnFlowEmbeddingProperties();
        properties.setEnabled(true);
        properties.setVersion("embedding-v2");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AgentHttpClient client = mock(AgentHttpClient.class);
        AsyncTaskService tasks = mock(AsyncTaskService.class);
        ResourceEmbeddingService service = new ResourceEmbeddingService(
                properties,
                jdbc,
                client,
                tasks,
                new SimpleMeterRegistry(),
                mock(PlatformTransactionManager.class)
        );

        assertThatThrownBy(() -> service.process(
                UUID.randomUUID(), UUID.randomUUID(), "embedding-v1"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("superseded");

        verifyNoInteractions(jdbc, client, tasks);
    }
}
