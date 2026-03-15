package com.learnflow.service;

import com.learnflow.entity.AdminAuditLog;
import com.learnflow.repository.AdminAuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;

    public AdminAuditLogService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String type, String operator, String targetType, Long targetId, String detail) {
        AdminAuditLog log = new AdminAuditLog();
        log.setType(type);
        log.setOperator(operator);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        repository.save(log);
    }

    public List<AdminAuditLog> recent() {
        return repository.findTop200ByOrderByCreatedAtDesc();
    }
}


