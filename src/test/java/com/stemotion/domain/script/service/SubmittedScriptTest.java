package com.stemotion.domain.script.service;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.repository.*;
import com.stemotion.domain.workspace.service.WorkspaceService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SubmittedScriptTest {
    @ParameterizedTest
    @EnumSource(value = ScriptStatus.class, names = {"IN_REVIEW", "APPROVED"})
    void reviewSnapshotCannotBeReplaced(ScriptStatus status) {
        var projects = mock(ClipProjectRepository.class);
        var scenes = mock(SceneRepository.class);
        var versions = mock(ScriptVersionRepository.class);
        when(projects.findById("project")).thenReturn(Optional.of(
            ClipProject.builder().scriptStatus(status).build()));
        var service = new ScriptService(projects, versions, scenes, mock(SceneMapper.class),
            mock(AuthService.class), mock(WorkspaceService.class), mock(StatusHistoryService.class),
            mock(WorkspaceAccess.class));
        assertThatThrownBy(() -> service.updateDraft("project", new UpdateScriptRequest(
            "Changed", null, null, null, null, java.util.List.of(), null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(scenes, versions);
        verify(projects, never()).save(any());
    }
}
