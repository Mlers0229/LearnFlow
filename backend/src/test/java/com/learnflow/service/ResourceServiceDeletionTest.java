package com.learnflow.service;

import com.learnflow.entity.ResourceBank;
import com.learnflow.repository.ResourceBankRepository;
import com.learnflow.repository.UserResourceFeedbackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceDeletionTest {

    @Mock private ResourceBankRepository resourceBankRepository;
    @Mock private UserResourceFeedbackRepository userResourceFeedbackRepository;
    @Mock private AdminAuditLogService auditLogService;
    @InjectMocks private ResourceService resourceService;

    @Test
    void ownerCanSoftDeleteFinishedResource() {
        ResourceBank resource = resource(1L, 7L, "PENDING", "FAILED");
        when(resourceBankRepository.findByIdAndStatusNot(1L, "DELETED")).thenReturn(Optional.of(resource));

        resourceService.deleteResource(1L, 7L, false);

        verify(resourceBankRepository).save(resource);
        verify(auditLogService).record("RESOURCE_DELETE", "user", "RESOURCE", 1L, "soft_deleted");
        org.junit.jupiter.api.Assertions.assertEquals("DELETED", resource.getStatus());
    }

    @Test
    void nonAdminCannotDeleteActiveResource() {
        when(resourceBankRepository.findByIdAndStatusNot(1L, "DELETED")).thenReturn(Optional.of(resource(1L, 7L, "ACTIVE", "SUCCEEDED")));

        ResourceDeletionException exception = assertThrows(ResourceDeletionException.class,
                () -> resourceService.deleteResource(1L, 7L, false));

        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE_RESOURCE_DELETE_FORBIDDEN", exception.getCode());
    }

    @Test
    void processingResourceCannotBeDeleted() {
        when(resourceBankRepository.findByIdAndStatusNot(1L, "DELETED")).thenReturn(Optional.of(resource(1L, 7L, "PENDING", "PROCESSING")));

        ResourceDeletionException exception = assertThrows(ResourceDeletionException.class,
                () -> resourceService.deleteResource(1L, 7L, true));

        org.junit.jupiter.api.Assertions.assertEquals("RESOURCE_INGESTION_IN_PROGRESS", exception.getCode());
    }

    private ResourceBank resource(long id, long ownerId, String status, String ingestionStatus) {
        ResourceBank resource = new ResourceBank();
        resource.setId(id);
        resource.setUploaderUserId(ownerId);
        resource.setStatus(status);
        resource.setIngestionStatus(ingestionStatus);
        return resource;
    }
}
