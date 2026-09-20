package com.stemotion.domain.admin.service;

import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.domain.admin.dto.AdminDtos.SystemMetrics;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * Numbers behind the admin dashboard.
 *
 * <p>Render throughput comes from the job table. Host CPU and memory come from
 * the JVM's view of the machine; GPU usage is not observable from here, so it
 * is reported as -1 rather than invented.
 */
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    /** Sentinel for a metric this process cannot measure. */
    private static final double UNAVAILABLE = -1d;

    private final RenderJobRepository renderJobRepository;

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
            ? 100d
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

    /** Returns -1 when the JVM cannot read a system load average (common on Windows). */
    private double cpuLoadPercent() {
        double load = java.lang.management.ManagementFactory
            .getOperatingSystemMXBean()
            .getSystemLoadAverage();
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
