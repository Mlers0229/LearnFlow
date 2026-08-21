package com.learnflow.controller;

import com.learnflow.entity.AdminAuditLog;
import com.learnflow.service.AdminAuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AdminAuditLogService auditLogService;

    public AdminAuditController(AdminAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AdminAuditLog>> recent() {
        return ResponseEntity.ok(auditLogService.recent());
    }
}


