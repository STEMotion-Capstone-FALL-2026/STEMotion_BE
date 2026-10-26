package com.stemotion.domain.script.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest;
import com.stemotion.domain.script.entity.*;
import com.stemotion.domain.script.mapper.*;
import com.stemotion.domain.script.repository.*;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Master update plan M17 "Revision": a draft save from an older revision never overwrites a newer one. */
class DraftRevisionTest {
    final SceneRepository scenes = mock(SceneRepository.class);
    final ClipProjectRepository projects = mock(ClipProjectRepository.class);
    final ScriptVersionRepository versions = mock(ScriptVersionRepository.class);
    final ScriptService service = new ScriptService(projects, versions, scenes,
        new SceneMapper(new ObjectMapper(), new ScenePropsValidator()), mock(AuthService.class),
        mock(WorkspaceService.class), mock(StatusHistoryService.class), mock(WorkspaceAccess.class));

    ClipProject project;
    ScriptVersion version;

    @BeforeEach
    void setUp() {
        var workspace = new Workspace(); workspace.setId("workspace");
        var owner = new User(); owner.setId("owner"); owner.setFullName("Owner");
        project = ClipProject.builder().title("Original").subject(StemSubject.Math).fps(30)
            .scriptStatus(ScriptStatus.DRAFT).videoStatus(VideoStatus.NOT_RENDERED).workspace(workspace).owner(owner).build();
        project.setId("project");
        version = ScriptVersion.builder().versionNo(1).title("Original").draftRevision(4).build();
        version.setId("version");
        when(projects.findById("project")).thenReturn(Optional.of(project));
        when(versions.lockCurrentByProjectId("project")).thenReturn(Optional.of(version));
        when(scenes.findByScriptVersionIdOrderByOrderNoAsc("version")).thenReturn(List.of());
        when(scenes.findForUpdateByScriptVersionId("version")).thenReturn(List.of());
    }

    UpdateScriptRequest titled(String title, Integer baseRevision) {
        return new UpdateScriptRequest(title, null, null, null, null, null, baseRevision);
    }

    @Test
    @DisplayName("a save from the current revision is applied and moves the draft to the next revision")
    void currentRevisionSaves() {
        var response = service.updateDraft("project", titled("Edited", 4));
        assertThat(response.draftRevision()).isEqualTo(5);
        assertThat(response.title()).isEqualTo("Edited");
        verify(versions).lockCurrentByProjectId("project");
        verify(versions, never()).findFirstByProjectIdAndIsCurrentTrue(any());
    }

    @Test
    @DisplayName("a save from an older revision is a 409 and changes nothing")
    void staleRevisionIsAConflict() {
        assertThatThrownBy(() -> service.updateDraft("project", titled("Stale tab", 3)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("revision 4")
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(409));
        assertThat(project.getTitle()).isEqualTo("Original");
        assertThat(version.getTitle()).isEqualTo("Original");
        assertThat(version.getDraftRevision()).isEqualTo(4);
        verify(versions, never()).save(any());
        verifyNoInteractions(scenes);
    }

    @Test
    @DisplayName("an older client without baseRevision still saves, and still bumps the revision")
    void missingBaseRevisionKeepsWorking() {
        var response = service.updateDraft("project", titled("Old client", null));
        assertThat(response.draftRevision()).isEqualTo(5);
    }

    @Test
    @DisplayName("measured durations change the draft, so they bump the revision too")
    void measuredDurationsBumpTheRevision() {
        var response = service.applyMeasuredDurations("project", Map.of());
        assertThat(response.draftRevision()).isEqualTo(5);
        assertThatThrownBy(() -> service.updateDraft("project", titled("Before sync", 4)))
            .isInstanceOf(ApiException.class).hasMessageContaining("revision 5");
    }
}
