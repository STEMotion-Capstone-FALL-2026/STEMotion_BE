package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.RenderCallbackRequest;
import com.stemotion.domain.production.dto.ProductionDtos.StartRenderRequest;
import com.stemotion.domain.production.entity.Composition;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.integration.client.RenderServiceClient;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BE-P0-5 / BE-P0-1: renders of a frozen composition and the theme they draw with. */
class FrozenCompositionRenderTest {

    final RenderJobRepository jobs = mock(RenderJobRepository.class);
    final ScriptService scripts = mock(ScriptService.class);
    final CompositionService compositions = mock(CompositionService.class);
    final RenderServiceClient worker = mock(RenderServiceClient.class);
    final IntegrationProperties properties = new IntegrationProperties();
    final RenderService service = new RenderService(jobs, mock(ClipProjectRepository.class), scripts, compositions,
        new com.fasterxml.jackson.databind.ObjectMapper(), worker, mock(AuthService.class), properties,
        mock(ClipArtifactService.class));

    ClipProject project;
    ScriptVersion version;

    @BeforeEach
    void setUp() {
        properties.getRender().setApiKey("key");
        Workspace workspace = Workspace.builder().name("Tổ Lý").build();
        workspace.setId("ws");
        project = ClipProject.builder().title("Ohm").subject(StemSubject.Physics).fps(30).workspace(workspace)
            .scriptStatus(ScriptStatus.APPROVED).videoStatus(VideoStatus.APPROVED).build();
        project.setId("project");
        version = new ScriptVersion();
        when(scripts.getOrThrow("project")).thenReturn(project);
        when(scripts.currentVersion(project)).thenReturn(version);
        Composition composition = Composition.builder().revision(4).visualTheme("cyber").scenes("[]").build();
        Map<String, Object> scene = new LinkedHashMap<>(Map.of("id", "s1", "type", "TITLE_HERO", "title", "Ohm",
            "narration", "N", "durationInFrames", 90));
        when(compositions.renderInput(version)).thenReturn(new CompositionService.RenderInput(composition, 4, List.of(scene)));
        when(jobs.save(any())).thenAnswer(i -> { RenderJob job = i.getArgument(0); if (job.getId() == null) job.setId("job"); return job; });
        when(worker.enqueue(anyString(), anyString(), anyMap(), anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(new RenderServiceClient.EnqueueResult("external", 5));
    }

    @Test
    @DisplayName("rendering an approved composition keeps the project APPROVED and draws its theme")
    void renderAfterApprovalKeepsApproval() {
        service.renderApproved("project", "reviewer");
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.APPROVED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> props = ArgumentCaptor.forClass(Map.class);
        verify(worker).enqueue(eq("job"), anyString(), props.capture(), anyInt(), anyInt(), anyInt(), any());
        assertThat(props.getValue()).containsEntry("visualTheme", "cyber").containsEntry("workspaceId", "ws::cyber");
    }

    @Test
    @DisplayName("a render already under way brings the approved clip instead of a second job")
    void noSecondJob() {
        when(jobs.existsByProjectIdAndStatusIn(eq("project"), anyCollection())).thenReturn(true);
        service.renderApproved("project", "reviewer");
        verifyNoInteractions(worker);
    }

    @Test
    @DisplayName("the clip of an approved composition arrives without sending it back to QA")
    void completionKeepsApproval() {
        RenderJob job = RenderJob.builder().project(project).scriptVersion(version).status(RenderStatus.PROCESSING)
            .progress(50).attemptCount(1).build();
        job.setId("job");
        when(jobs.findById("job")).thenReturn(Optional.of(job));
        service.applyCallback("key", new RenderCallbackRequest("job", RenderStatus.COMPLETED, 100, "Done",
            "https://videos/clip.mp4", null));
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.APPROVED);
        assertThat(project.getVideoUrl()).isEqualTo("https://videos/clip.mp4");
    }

    @Test
    @DisplayName("a published clip is not rendered again")
    void publishedIsFinal() {
        project.setVideoStatus(VideoStatus.PUBLISHED);
        assertThatThrownBy(() -> service.start("project", new StartRenderRequest(null, null, null), "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("published");
    }
}
