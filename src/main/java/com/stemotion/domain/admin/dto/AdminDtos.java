package com.stemotion.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    public record AdminRenderJobSummary(
        String id,
        String externalJobId,
        String projectId,
        String projectTitle,
        String subject,
        String requestedByName,
        String status,
        int progress,
        String stage,
        String resolution,
        int fps,
        String videoUrl,
        String errorMessage,
        int attemptCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    /** Nullable usage fields mean unavailable until real request aggregation is wired. */
    public record AiUsageStats(
        long totalProjects,
        long totalScriptVersions,
        long totalScenesGenerated,
        Long estimatedAiCalls,
        Long avgLatencyMs,
        Long estimatedTokensUsed,
        Double estimatedCostUsd,
        Double monthlyBudgetUsd,
        String modelName,
        String ttsProvider
    ) {}

    public record AdminDashboardOverview(
        SystemMetrics metrics,
        AiUsageStats aiUsage,
        List<AdminRenderJobSummary> recentRenderJobs,
        Map<String, Long> projectsBySubject
    ) {}
}
