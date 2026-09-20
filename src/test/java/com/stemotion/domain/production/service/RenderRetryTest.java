package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.RenderResolution;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.RenderCallbackRequest;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.integration.client.RenderServiceClient;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The proposal allows a failed render three attempts before anyone is alerted,
 * so a transient worker crash must requeue itself rather than wait for a human.
 */
class RenderRetryTest {

    private static final String RENDER_KEY = "test-render-key";

    private RenderJobRepository renderJobRepository;
    private ClipProjectRepository projectRepository;
    private ScriptService scriptService;
    private RenderServiceClient renderClient;
    private IntegrationProperties properties;
    private RenderService renderService;

    private ClipProject project;

    @BeforeEach
    void setUp() {
        renderJobRepository = mock(RenderJobRepository.class);
        projectRepository = mock(ClipProjectRepository.class);
        scriptService = mock(ScriptService.class);
        renderClient = mock(RenderServiceClient.class);
        properties = new IntegrationProperties();
        properties.getRender().setMaxAttempts(3);
        // The callback is now authenticated with the shared render key.
        properties.getRender().setApiKey(RENDER_KEY);

        renderService = new RenderService(
            renderJobRepository,
            projectRepository,
            scriptService,
            mock(SceneMapper.class),
            renderClient,
            mock(AuthService.class),
            properties
        );

        project = ClipProject.builder()
            .title("Dinh ly Vi-et")
            .subject(StemSubject.Math)
            .fps(30)
            .scriptStatus(ScriptStatus.APPROVED)
            .videoStatus(VideoStatus.RENDERING)
            .build();
        project.setId("project-1");

        when(scriptService.scenesOf(any())).thenReturn(List.of());
        when(renderJobRepository.save(any(RenderJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(renderClient.enqueue(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(new RenderServiceClient.EnqueueResult("external-2", 45));
    }

    private RenderJob failingJobOnAttempt(int attempt) {
        RenderJob job = RenderJob.builder()
            .project(project)
            .scriptVersion(new ScriptVersion())
            .status(RenderStatus.FAILED)
            .progress(40)
            .resolution(RenderResolution.RES_1080P)
            .fps(30)
            .attemptCount(attempt)
            .build();
        job.setId("job-1");
        when(renderJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        return job;
    }

    private RenderCallbackRequest failureCallback() {
        return new RenderCallbackRequest("job-1", RenderStatus.FAILED, 40, "Crashed", null, "Chromium died");
    }

    @Test
    @DisplayName("a first failure is requeued automatically")
    void requeuesOnFirstFailure() {
        RenderJob job = failingJobOnAttempt(1);

        renderService.applyCallback(RENDER_KEY, failureCallback());

        verify(renderClient).enqueue(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), any());
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(RenderStatus.QUEUED);
        assertThat(job.getProgress()).isZero();
    }

    @Test
    @DisplayName("the project stays in production while a retry is queued")
    void keepsProjectRenderingBetweenAttempts() {
        failingJobOnAttempt(1);

        renderService.applyCallback(RENDER_KEY, failureCallback());

        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.RENDERING);
    }

    @Test
    @DisplayName("the retry stage tells the producer which attempt is running")
    void labelsTheAttempt() {
        RenderJob job = failingJobOnAttempt(2);

        renderService.applyCallback(RENDER_KEY, failureCallback());

        assertThat(job.getStage()).isEqualTo("Thử lại lần 3/3");
    }

    @Test
    @DisplayName("the attempt budget is not exceeded")
    void stopsAtTheAttemptBudget() {
        RenderJob job = failingJobOnAttempt(3);

        renderService.applyCallback(RENDER_KEY, failureCallback());

        verify(renderClient, never()).enqueue(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), any());
        assertThat(job.getStatus()).isEqualTo(RenderStatus.FAILED);
        assertThat(job.getAttemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("after the last attempt the clip falls back to not rendered")
    void releasesProjectAfterFinalFailure() {
        failingJobOnAttempt(3);

        renderService.applyCallback(RENDER_KEY, failureCallback());

        ArgumentCaptor<ClipProject> saved = ArgumentCaptor.forClass(ClipProject.class);
        verify(projectRepository).save(saved.capture());
        assertThat(saved.getValue().getVideoStatus()).isEqualTo(VideoStatus.NOT_RENDERED);
    }

    @Test
    @DisplayName("an unreachable render service does not spin on retries")
    void doesNotSpinWhenRequeueFails() {
        RenderJob job = failingJobOnAttempt(1);
        when(renderClient.enqueue(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), any()))
            .thenThrow(new RuntimeException("connection refused"));

        renderService.applyCallback(RENDER_KEY, failureCallback());

        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.NOT_RENDERED);
        assertThat(job.getErrorMessage()).contains("retry failed");
    }

    @Test
    @DisplayName("a completed render is never retried")
    void completedRenderIsNotRetried() {
        RenderJob job = failingJobOnAttempt(1);
        job.setStatus(RenderStatus.PROCESSING);

        renderService.applyCallback(
            RENDER_KEY,
            new RenderCallbackRequest("job-1", RenderStatus.COMPLETED, 100, "Done", "http://x/v.mp4", null)
        );

        verify(renderClient, never()).enqueue(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), any());
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.IN_QA);
        assertThat(project.getVideoUrl()).isEqualTo("http://x/v.mp4");
    }

    @Test
    @DisplayName("a callback without the shared key is rejected")
    void rejectsMissingApiKey() {
        failingJobOnAttempt(1);

        assertThatThrownBy(() -> renderService.applyCallback(null, failureCallback()))
            .isInstanceOf(ApiException.class);
        verify(renderClient, never()).enqueue(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("a callback with the wrong key is rejected")
    void rejectsWrongApiKey() {
        failingJobOnAttempt(1);

        assertThatThrownBy(() -> renderService.applyCallback("not-the-key", failureCallback()))
            .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("the scene payload is rebuilt for the retry, not reused blindly")
    void rebuildsPayloadOnRetry() {
        failingJobOnAttempt(1);

        renderService.applyCallback(RENDER_KEY, failureCallback());

        verify(scriptService).scenesOf(any());
        ArgumentCaptor<Map<String, Object>> props = ArgumentCaptor.forClass(Map.class);
        verify(renderClient).enqueue(
            anyString(), anyString(), props.capture(), anyInt(), anyInt(), anyInt(), any()
        );
        assertThat(props.getValue()).containsKey("scenes");
    }
}
