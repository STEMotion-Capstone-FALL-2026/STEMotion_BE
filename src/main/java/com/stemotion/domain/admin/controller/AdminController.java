package com.stemotion.domain.admin.controller;

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

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "System monitoring for the admin dashboard")
public class AdminController {

    private final AdminMetricsService adminMetricsService;

    @GetMapping("/metrics")
    @Operation(summary = "Render queue, throughput and host resource usage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemMetrics> metrics() {
        return ResponseEntity.ok(adminMetricsService.collect());
    }
}
