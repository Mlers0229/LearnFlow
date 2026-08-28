package com.learnflow.service;

import com.learnflow.entity.ResourceBank;
import com.learnflow.entity.ResourceIngestion;
import com.learnflow.repository.ResourceBankRepository;
import com.learnflow.repository.ResourceIngestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceSourceAccessServiceTest {

    @Mock private ResourceBankRepository resources;
    @Mock private ResourceIngestionRepository ingestions;
    @Mock private ResourceSourceStore sourceStore;
    @InjectMocks private ResourceSourceAccessService service;

    @Test
    void ownerCanPreviewFinishedTextWithoutPublishingIt() throws Exception {
        UUID ingestionId = UUID.randomUUID();
        ResourceBank resource = resource(1L, 7L, "PENDING", "TEXT", ingestionId);
        ResourceIngestion ingestion = ingestion(ingestionId, 1L, "text/html", "notes.md", "source-key");
        byte[] source = "# 安全正文".getBytes(StandardCharsets.UTF_8);
        when(resources.findByIdAndStatusNot(1L, "DELETED")).thenReturn(Optional.of(resource));
        when(ingestions.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(sourceStore.open("source-key")).thenReturn(new ByteArrayInputStream(source));

        ResourceSourceAccessService.SourceArtifact artifact = service.open(1L, 7L, false);

        assertThat(artifact.viewMode()).isEqualTo(ResourceSourceAccessService.VIEW_TEXT);
        assertThat(artifact.contentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(artifact.filename()).isEqualTo("notes.md");
        assertThat(artifact.inputStream().readAllBytes()).isEqualTo(source);
    }

    @Test
    void authenticatedUserCanViewAnotherUsersActivePdf() throws Exception {
        UUID ingestionId = UUID.randomUUID();
        ResourceBank resource = resource(2L, 7L, "ACTIVE", "DOCUMENT", ingestionId);
        ResourceIngestion ingestion = ingestion(ingestionId, 2L, "application/pdf", "lesson.pdf", "pdf-key");
        when(resources.findByIdAndStatusNot(2L, "DELETED")).thenReturn(Optional.of(resource));
        when(ingestions.findById(ingestionId)).thenReturn(Optional.of(ingestion));
        when(sourceStore.open("pdf-key")).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));

        ResourceSourceAccessService.SourceArtifact artifact = service.open(2L, 99L, false);

        assertThat(artifact.viewMode()).isEqualTo(ResourceSourceAccessService.VIEW_PDF);
        assertThat(artifact.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void nonOwnerCannotDiscoverUnpublishedSource() throws Exception {
        UUID ingestionId = UUID.randomUUID();
        when(resources.findByIdAndStatusNot(3L, "DELETED"))
                .thenReturn(Optional.of(resource(3L, 7L, "PENDING", "DOCUMENT", ingestionId)));

        assertThatThrownBy(() -> service.open(3L, 99L, false))
                .isInstanceOfSatisfying(ResourceSourceAccessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("RESOURCE_SOURCE_NOT_FOUND");
                });
        verify(sourceStore, never()).open(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void processingSourceReturnsStableConflict() {
        ResourceBank resource = resource(4L, 7L, "PENDING", "TEXT", null);
        resource.setIngestionStatus("PROCESSING");
        when(resources.findByIdAndStatusNot(4L, "DELETED")).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> service.open(4L, 7L, false))
                .isInstanceOfSatisfying(ResourceSourceAccessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("RESOURCE_SOURCE_NOT_READY");
                });
    }

    private ResourceBank resource(long id, long ownerId, String status, String sourceType, UUID ingestionId) {
        ResourceBank resource = new ResourceBank();
        resource.setId(id);
        resource.setUploaderUserId(ownerId);
        resource.setStatus(status);
        resource.setSourceType(sourceType);
        resource.setIngestionStatus("SUCCEEDED");
        resource.setCurrentIngestionId(ingestionId);
        return resource;
    }

    private ResourceIngestion ingestion(UUID id, long resourceId, String contentType, String filename, String objectKey) {
        ResourceIngestion ingestion = new ResourceIngestion();
        ingestion.setId(id);
        ingestion.setResourceId(resourceId);
        ingestion.setStatus("SUCCEEDED");
        ingestion.setContentType(contentType);
        ingestion.setContentLength(3L);
        ingestion.setContentSha256("a".repeat(64));
        ingestion.setOriginalFilename(filename);
        ingestion.setObjectKey(objectKey);
        return ingestion;
    }
}
