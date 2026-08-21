package com.learnflow.repository;

import com.learnflow.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findTop200ByOrderByCreatedAtDesc();

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}


