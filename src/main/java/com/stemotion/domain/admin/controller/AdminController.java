package com.stemotion.domain.admin.controller;

import com.stemotion.domain.admin.dto.AdminDtos.AdminDashboardOverview;
import com.stemotion.domain.admin.dto.AdminDtos.AdminRenderJobSummary;
import com.stemotion.domain.admin.dto.AdminDtos.SystemMetrics;
import com.stemotion.domain.admin.service.AdminMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "System monitoring, render queue & AI cost tracking for the admin dashboard")
public class AdminController {

    private final AdminMetricsService adminMetricsService;

    @GetMapping("/metrics")
    @Operation(summary = "Render queue, throughput and host resource usage")
    public ResponseEntity<SystemMetrics> metrics() {
        return ResponseEntity.ok(adminMetricsService.collect());
    }

    @GetMapping("/overview")
    @Operation(summary = "Complete FE-17 operational overview: metrics, AI cost/usage, render jobs and STEM subject distribution")
    public ResponseEntity<AdminDashboardOverview> overview() {
        return ResponseEntity.ok(adminMetricsService.collectOverview());
    }

    @GetMapping("/render-jobs")
    @Operation(summary = "List recent render jobs for queue monitoring")
    public ResponseEntity<List<AdminRenderJobSummary>> renderJobs() {
        return ResponseEntity.ok(adminMetricsService.listRecentRenderJobs());
    }
}
