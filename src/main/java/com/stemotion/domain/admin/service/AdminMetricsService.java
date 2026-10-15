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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

    @Value("${app.integration.gemini.model}")
    private String geminiModel;

    @Value("${AI_MONTHLY_BUDGET_USD:#{null}}")
    private Double monthlyBudgetUsd;

    @Transactional(readOnly = true)
    public SystemMetrics collect() {
        List<RenderJob> jobs = renderJobRepository.findAll();
        LocalDate today = LocalDate.now();

        long active = jobs.stream()
            .filter(j -> j.getStatus() == RenderStatus.QUEUED || j.getStatus() == RenderStatus.PROCESSING)
            .count();

        List<RenderJob> finishedToday = jobs.stream()
            .filter(j -> j.getUpdatedAt() != null && j.getUpdatedAt().toLocalDate().equals(today))
            .filter(j -> j.getStatus() == RenderStatus.COMPLETED || j.getStatus() == RenderStatus.FAILED)
            .toList();

        long completedToday = finishedToday.stream()
            .filter(j -> j.getStatus() == RenderStatus.COMPLETED)
            .count();
        long failedToday = finishedToday.size() - completedToday;

        double successRate = finishedToday.isEmpty()
            ? UNAVAILABLE
            : round((double) completedToday / finishedToday.size() * 100d);

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

    @Transactional(readOnly = true)
    public List<AdminRenderJobSummary> listRecentRenderJobs() {
        return renderJobRepository.findAll().stream()
            .sorted(Comparator.comparing(
                RenderJob::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .limit(25)
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
        List<ClipProject> projects = clipProjectRepository.findAll();
        long totalProjects = projects.size();
        long totalScriptVersions = scriptVersionRepository.count();
        long totalScenes = sceneRepository.count();

        // Content counts cannot establish provider calls, token usage, cost or latency.
        // Keep the overview contract, returning unknown until M01 aggregation is implemented.
        AiUsageStats aiUsage = new AiUsageStats(
            totalProjects, totalScriptVersions, totalScenes,
            null, null, null, null, monthlyBudgetUsd, geminiModel, null
        );

        Map<String, Long> projectsBySubject = new LinkedHashMap<>();
        projectsBySubject.put("Math", 0L);
        projectsBySubject.put("Physics", 0L);
        projectsBySubject.put("Chemistry", 0L);
        projectsBySubject.put("Biology", 0L);
        projectsBySubject.put("ComputerScience", 0L);
        for (ClipProject p : projects) {
            if (p.getSubject() != null) {
                String key = p.getSubject().name();
                projectsBySubject.put(key, projectsBySubject.getOrDefault(key, 0L) + 1L);
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
