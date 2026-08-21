package com.learnflow.service;

import com.learnflow.entity.AdminAuditLog;
import com.learnflow.repository.AdminAuditLogRepository;
import com.learnflow.config.LearnFlowAuditProperties;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;
    private final LearnFlowAuditProperties properties;

    public AdminAuditLogService(AdminAuditLogRepository repository, LearnFlowAuditProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public void record(String type, String operator, String targetType, Long targetId, String detail) {
        AdminAuditLog log = new AdminAuditLog();
        log.setType(type);
        log.setOperator(operator);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(boundDetail(detail));
        repository.save(log);
    }

    public List<AdminAuditLog> recent() {
        return repository.findTop200ByOrderByCreatedAtDesc();
    }

    @Transactional
    @Scheduled(cron = "${learnflow.audit.cleanup-cron:0 17 3 * * *}", zone = "UTC")
    public long purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getRetention());
        return repository.deleteByCreatedAtBefore(cutoff);
    }

    private String boundDetail(String detail) {
        if (detail == null) {
            return null;
        }
        int max = Math.max(128, properties.getMaxDetailLength());
        return detail.length() <= max ? detail : detail.substring(0, max);
    }
}


