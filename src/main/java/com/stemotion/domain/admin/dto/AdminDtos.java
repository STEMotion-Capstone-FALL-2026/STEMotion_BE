package com.stemotion.domain.admin.dto;

public final class AdminDtos {

    private AdminDtos() {}

    /**
     * Mirrors the SystemMetrics interface consumed by the admin dashboard.
     * Percentages are 0-100; storage is in gigabytes.
     */
    public record SystemMetrics(
        double cpuUsage,
        double gpuUsage,
        double memoryUsage,
        long activeRenderJobs,
        long completedToday,
        double storageUsedGb,
        long failedToday,
        double successRate
    ) {}
}
