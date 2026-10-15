package com.stemotion.domain.admin.service;

import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminMetricsServiceTest {
    final RenderJobRepository jobs = mock(RenderJobRepository.class);
    final ClipProjectRepository projects = mock(ClipProjectRepository.class);
    final ScriptVersionRepository versions = mock(ScriptVersionRepository.class);
    final SceneRepository scenes = mock(SceneRepository.class);
    final AdminMetricsService service = new AdminMetricsService(jobs, projects, versions, scenes);

    @Test void contentCountsDoNotInventProviderUsageOrCost() {
        when(versions.count()).thenReturn(100L);
        when(scenes.count()).thenReturn(500L);
        var usage = service.collectOverview().aiUsage();
        assertThat(usage.totalScriptVersions()).isEqualTo(100);
        assertThat(usage.totalScenesGenerated()).isEqualTo(500);
        assertThat(usage.estimatedAiCalls()).isNull();
        assertThat(usage.avgLatencyMs()).isNull();
        assertThat(usage.estimatedTokensUsed()).isNull();
        assertThat(usage.estimatedCostUsd()).isNull();
        assertThat(usage.monthlyBudgetUsd()).isNull();
        assertThat(usage.ttsProvider()).isNull();
    }

    @Test void historicalJobsDoNotBecomeTodaysCompletions() {
        var old = RenderJob.builder().status(RenderStatus.COMPLETED).build();
        old.setUpdatedAt(LocalDateTime.now().minusDays(1));
        when(jobs.findAll()).thenReturn(List.of(old));
        var metrics = service.collect();
        assertThat(metrics.completedToday()).isZero();
        assertThat(metrics.failedToday()).isZero();
        assertThat(metrics.successRate()).isEqualTo(-1d);
    }
}
