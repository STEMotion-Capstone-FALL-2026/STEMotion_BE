package com.stemotion.domain.production.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.production.dto.ProductionDtos.CompositionResponse;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptResponse;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.integration.client.RenderServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NarrationToolsServiceTest {

    final ScriptService scripts = mock(ScriptService.class);
    final CompositionService compositions = mock(CompositionService.class);
    final RenderServiceClient worker = mock(RenderServiceClient.class);
    final WorkspaceAccess access = mock(WorkspaceAccess.class);
    final ObjectMapper json = new ObjectMapper();
    final NarrationToolsService service = new NarrationToolsService(scripts, compositions, worker, access, json);

    ClipProject project;
    ScriptVersion version;

    @BeforeEach
    void setUp() throws Exception {
        project = ClipProject.builder().fps(30).archived(false)
            .scriptStatus(ScriptStatus.APPROVED).videoStatus(VideoStatus.NOT_RENDERED).build();
        project.setId("project");
        version = new ScriptVersion();
        when(scripts.getOrThrow("project")).thenReturn(project);
        when(scripts.currentVersion(project)).thenReturn(version);
        Map<String, Object> scene = new LinkedHashMap<>(Map.of("id", "s1", "narration", "Xin chào các em", "durationInFrames", 60));
        when(compositions.renderInput(version)).thenReturn(new CompositionService.RenderInput(null, null, List.of(scene)));
        when(compositions.scriptScenes(version)).thenReturn(List.of(scene));
        when(worker.syncDurations(anyMap(), eq(30))).thenReturn(json.readTree("""
            {"script":{"scenes":[{"id":"s1","durationInFrames":96,
              "captionWords":[{"text":"Xin","start":0.1,"end":0.3}]}]},
             "captionSync":{"status":"ALIGNED"}}"""));
    }

    @Test
    void anApprovedScriptsCompositionGetsANewRevisionWithCaptionWords() {
        when(compositions.applyMeasured(eq("project"), eq(2), anyMap(), anyMap(), eq("producer")))
            .thenReturn(new CompositionResponse("project", 1, 3, true, null,
                List.of(Map.of("id", "s1", "durationInFrames", 96)), null, null, "default"));
        var response = service.syncDurations("project", 2, "producer");
        assertThat(response.target()).isEqualTo("COMPOSITION");
        assertThat(response.revision()).isEqualTo(3);
        assertThat(response.totalDurationSeconds()).isEqualTo(3);
        assertThat(response.scenes().get(0)).containsEntry("durationInFrames", 96).containsKey("captionWords");
        assertThat(response.captionSync().path("status").asText()).isEqualTo("ALIGNED");
        verify(access).requireProjectRole("project", "producer", Set.of(User.UserRole.PRODUCER));
        verify(compositions).applyMeasured(eq("project"), eq(2), eq(Map.of("s1", 96)), anyMap(), eq("producer"));

        assertThatThrownBy(() -> service.syncDurations("project", null, "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("baseRevision");
    }

    @Test
    void aDraftHasItsSceneRowsTimedByTheWriter() {
        project.setScriptStatus(ScriptStatus.DRAFT);
        ScriptResponse draft = mock(ScriptResponse.class);
        when(draft.totalDurationSeconds()).thenReturn(4);
        when(draft.draftRevision()).thenReturn(7);
        when(scripts.applyMeasuredDurations("project", Map.of("s1", 96))).thenReturn(draft);
        var response = service.syncDurations("project", null, "writer");
        assertThat(response.target()).isEqualTo("SCRIPT");
        assertThat(response.totalDurationSeconds()).isEqualTo(4);
        assertThat(response.revision()).as("the new draft revision").isEqualTo(7);
        verify(access).requireProjectRole("project", "writer", Set.of(User.UserRole.WRITER));
    }

    @Test
    void aPreviewReadsTheSceneNarrationAndRefusesLongText() {
        when(worker.ttsPreview("Xin chào các em")).thenReturn(new byte[] {1, 2});
        assertThat(service.ttsPreview("project", null, "s1")).containsExactly(1, 2);
        assertThatThrownBy(() -> service.ttsPreview("project", "a".repeat(1501), null))
            .isInstanceOf(ApiException.class).hasMessageContaining("1500");
        verify(worker, never()).ttsPreview(startsWith("aaa"));
    }
}
