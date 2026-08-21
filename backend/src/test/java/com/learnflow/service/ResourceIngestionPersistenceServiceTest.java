package com.learnflow.service;

import com.learnflow.entity.ResourceBank;
import com.learnflow.entity.ResourceChunk;
import com.learnflow.entity.ResourceIngestion;
import com.learnflow.entity.ResourceIngestionChunk;
import com.learnflow.repository.ResourceBankRepository;
import com.learnflow.repository.ResourceChunkRepository;
import com.learnflow.repository.ResourceIngestionChunkRepository;
import com.learnflow.repository.ResourceIngestionRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceIngestionPersistenceServiceTest {
    @Test
    void newVersionReusesUnchangedChunkIdentityAndCreatesOnlyChangedChunk() {
        ResourceBankRepository resources = mock(ResourceBankRepository.class);
        ResourceIngestionRepository ingestions = mock(ResourceIngestionRepository.class);
        ResourceChunkRepository chunks = mock(ResourceChunkRepository.class);
        ResourceIngestionChunkRepository links = mock(ResourceIngestionChunkRepository.class);
        ResourceIngestionPersistenceService service = new ResourceIngestionPersistenceService(resources, ingestions, chunks, links);
        UUID ingestionId = UUID.randomUUID();
        UUID unchangedId = UUID.randomUUID();
        ResourceIngestion ingestion = new ResourceIngestion();
        ingestion.setId(ingestionId);
        ingestion.setResourceId(42L);
        ingestion.setStatus("PROCESSING");
        ResourceBank resource = new ResourceBank();
        resource.setId(42L);
        ResourceChunk unchanged = new ResourceChunk();
        unchanged.setId(unchangedId);
        unchanged.setResourceId(42L);
        unchanged.setContentHash("a".repeat(64));
        unchanged.setContent("unchanged");
        unchanged.setCreatedAt(OffsetDateTime.now());
        when(ingestions.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(resources.findById(42L)).thenReturn(Optional.of(resource));
        when(chunks.findByResourceIdAndContentHashIn(org.mockito.ArgumentMatchers.eq(42L), anyList()))
                .thenReturn(List.of(unchanged));
        when(chunks.save(any(ResourceChunk.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResourceContentProcessor.ProcessedContent content = new ResourceContentProcessor.ProcessedContent(
                "unchanged changed".getBytes(), "text/plain", "c".repeat(64), "en", 17,
                List.of(
                        new ResourceContentProcessor.Chunk(0, 0, 9, "unchanged", "a".repeat(64)),
                        new ResourceContentProcessor.Chunk(1, 10, 17, "changed", "b".repeat(64))
                )
        );

        assertThat(service.complete(ingestionId, content, "object-key")).isEqualTo(42L);
        verify(chunks, times(1)).save(any(ResourceChunk.class));
        verify(links, times(2)).save(any(ResourceIngestionChunk.class));
        assertThat(resource.getCurrentIngestionId()).isEqualTo(ingestionId);
        assertThat(resource.getIngestionStatus()).isEqualTo("SUCCEEDED");
    }
}
