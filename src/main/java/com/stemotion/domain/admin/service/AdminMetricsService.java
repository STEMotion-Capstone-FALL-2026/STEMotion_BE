package com.stemotion.domain.admin.service;

import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.domain.admin.dto.AdminDtos.AdminDashboardOverview;
import com.stemotion.domain.admin.dto.AdminDtos.AdminRenderJobSummary;
import com.stemotion.domain.admin.dto.AdminDtos.AiUsageStats;
import com.stemotion.domain.admin.dto.AdminDtos.SystemMetrics;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numbers behind the admin dashboard (FE-17: Monitor Render Queue & Storage and Monitor AI Cost & Usage).
 */
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    /** Sentinel for a metric this process cannot measure. */
    private static final double UNAVAILABLE = -1d;

    private final RenderJobRepository renderJobRepository;
    private final ClipProjectRepository clipProjectRepository;
    private final ScriptVersionRepository scriptVersionRepository;
    private final SceneRepository sceneRepository;
    private final com.stemotion.domain.ai.repository.AiRequestRepository aiRequestRepository;
    private final com.stemotion.integration.config.IntegrationProperties integrationProperties;


    @Value("${AI_MONTHLY_BUDGET_USD:#{null}}")
    private Double monthlyBudgetUsd;

    /**
     * Render counters from two grouped queries (DB performance plan 2.10), not by reading every job:
     * active = queued + processing now; completed/failed = jobs that ended today.
     */
    @Transactional(readOnly = true)
    public SystemMetrics collect() {
        Map<RenderStatus, Long> now = byStatus(renderJobRepository.countByStatus());
        long active = now.getOrDefault(RenderStatus.QUEUED, 0L) + now.getOrDefault(RenderStatus.PROCESSING, 0L);

        Map<RenderStatus, Long> today = byStatus(
            renderJobRepository.countByStatusUpdatedSince(LocalDate.now().atStartOfDay()));
        long completedToday = today.getOrDefault(RenderStatus.COMPLETED, 0L);
        long failedToday = today.getOrDefault(RenderStatus.FAILED, 0L);
        long finishedToday = completedToday + failedToday;

        double successRate = finishedToday == 0
            ? UNAVAILABLE
            : round((double) completedToday / finishedToday * 100d);

        Runtime runtime = Runtime.getRuntime();
        double usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsage = round(usedMemory / runtime.maxMemory() * 100d);

        File root = new File(".");
        double usedBytes = root.getTotalSpace() - root.getUsableSpace();
        double storageUsedGb = round(usedBytes / (1024d * 1024d * 1024d));

        return new SystemMetrics(
            round(cpuLoadPercent()),
            UNAVAILABLE,
            memoryUsage,
            active,
            completedToday,
            storageUsedGb,
            failedToday,
            successRate
        );
    }

    private static Map<RenderStatus, Long> byStatus(List<Object[]> rows) {
        Map<RenderStatus, Long> map = new java.util.EnumMap<>(RenderStatus.class);
        for (Object[] row : rows) {
            if (row[0] instanceof RenderStatus status) map.put(status, ((Number) row[1]).longValue());
        }
        return map;
    }

    /** The 25 newest jobs, limited in SQL, with project and requester joined in. */
    @Transactional(readOnly = true)
    public List<AdminRenderJobSummary> listRecentRenderJobs() {
        return renderJobRepository.findRecentWithProject(org.springframework.data.domain.PageRequest.of(0, 25)).stream()
            .map(job -> new AdminRenderJobSummary(
                job.getId(),
                job.getExternalJobId(),
                job.getProject() != null ? job.getProject().getId() : "",
                job.getProject() != null ? job.getProject().getTitle() : "Dự án STEM",
                job.getProject() != null && job.getProject().getSubject() != null
                    ? job.getProject().getSubject().name()
                    : "Physics",
                job.getRequestedBy() != null ? job.getRequestedBy().getFullName() : "Producer",
                job.getStatus() != null ? job.getStatus().name() : "QUEUED",
                job.getProgress() != null ? job.getProgress() : 0,
                job.getStage(),
                job.getResolution() != null ? job.getResolution().name() : "RES_1080P",
                job.getFps() != null ? job.getFps() : 30,
                job.getVideoUrl(),
                job.getErrorMessage(),
                job.getAttemptCount() != null ? job.getAttemptCount() : 0,
                job.getCreatedAt(),
                job.getUpdatedAt()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminDashboardOverview collectOverview() {
        SystemMetrics metrics = collect();
        List<Object[]> bySubject = clipProjectRepository.countBySubject();
        long totalProjects = bySubject.stream().mapToLong(row -> ((Number) row[1]).longValue()).sum();
        long totalScriptVersions = scriptVersionRepository.count();
        long totalScenes = sceneRepository.count();

        // Measured, not estimated: every AI call is recorded in ai_requests with
        // its latency, tokens and (when the gateway knows it) its cost. The DTO
        // keeps its "estimated" field names so the dashboard needs no change.
        var usage = aiRequestRepository.usageTotals();
        long estimatedAiCalls = usage.getCalls();
        long avgLatencyMs = usage.getAvgLatencyMs() == null ? 0L : Math.round(usage.getAvgLatencyMs());
        long estimatedTokens = usage.getTokens() == null ? 0L : usage.getTokens();
        Double estimatedCostUsd = usage.getCost() == null ? null : usage.getCost().doubleValue();

        AiUsageStats aiUsage = new AiUsageStats(
            totalProjects,
            totalScriptVersions,
            totalScenes,
            estimatedAiCalls,
            avgLatencyMs,
            estimatedTokens,
            estimatedCostUsd,
            monthlyBudgetUsd,
            integrationProperties.getGemini().getModel(),
            null
        );

        Map<String, Long> projectsBySubject = new LinkedHashMap<>();
        projectsBySubject.put("Math", 0L);
        projectsBySubject.put("Physics", 0L);
        projectsBySubject.put("Chemistry", 0L);
        projectsBySubject.put("Biology", 0L);
        projectsBySubject.put("ComputerScience", 0L);
        for (Object[] row : bySubject) {
            if (row[0] instanceof com.stemotion.core.enums.StemEnums.StemSubject subject) {
                projectsBySubject.merge(subject.name(), ((Number) row[1]).longValue(), Long::sum);
            }
        }

        return new AdminDashboardOverview(
            metrics,
            aiUsage,
            listRecentRenderJobs(),
            projectsBySubject
        );
    }

    /** Uses OperatingSystemMXBean CPU load (works on Windows & Linux). */
    private double cpuLoadPercent() {
        var osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            double cpuLoad = sunBean.getCpuLoad();
            if (cpuLoad >= 0) {
                return Math.min(100d, cpuLoad * 100d);
            }
        }
        double load = osBean.getSystemLoadAverage();
        if (load < 0) {
            return UNAVAILABLE;
        }
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.min(100d, load / cores * 100d);
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }
}
