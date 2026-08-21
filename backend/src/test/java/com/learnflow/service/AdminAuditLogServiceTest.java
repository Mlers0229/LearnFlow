package com.learnflow.service;

import com.learnflow.config.LearnFlowAuditProperties;
import com.learnflow.entity.AdminAuditLog;
import com.learnflow.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAuditLogServiceTest {

    @Test
    void boundsDetailAndPurgesUsingConfiguredRetention() {
        AdminAuditLogRepository repository = mock(AdminAuditLogRepository.class);
        LearnFlowAuditProperties properties = new LearnFlowAuditProperties();
        properties.setRetention(Duration.ofDays(90));
        properties.setMaxDetailLength(128);
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(4L);
        AdminAuditLogService service = new AdminAuditLogService(repository, properties);

        service.record("TEST", "admin", "USER", 1L, "x".repeat(300));
        long purged = service.purgeExpired();

        ArgumentCaptor<AdminAuditLog> saved = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDetail()).hasSize(128);
        assertThat(purged).isEqualTo(4L);
        verify(repository).deleteByCreatedAtBefore(argThat(cutoff ->
                cutoff.isBefore(LocalDateTime.now().minusDays(89))));
    }
}
