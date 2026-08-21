package com.learnflow.controller;

import com.learnflow.dto.AdminDashboardSummaryDto;
import com.learnflow.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public ResponseEntity<AdminDashboardSummaryDto> getDashboardSummary(
            @RequestParam(name = "logLimit", defaultValue = "120") int logLimit,
            @RequestParam(name = "planLimit", defaultValue = "50") int planLimit,
            @RequestParam(name = "trendDays", defaultValue = "7") int trendDays) {
        return ResponseEntity.ok(adminDashboardService.getDashboardSummary(logLimit, planLimit, trendDays));
    }
}
