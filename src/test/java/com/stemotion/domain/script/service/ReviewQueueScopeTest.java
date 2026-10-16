package com.stemotion.domain.script.service;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.service.WorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Roles are per workspace: someone who reviews for one department and only
 * writes for another sees, in the review queue, only the first department's
 * scripts, since they could not decide on the others.
 */
class ReviewQueueScopeTest {

    private final ClipProjectRepository projects = mock(ClipProjectRepository.class);
    private final ScriptVersionRepository versions = mock(ScriptVersionRepository.class);
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);
    private final ScriptService service = new ScriptService(projects, versions, mock(SceneRepository.class), null,
        mock(AuthService.class), mock(WorkspaceService.class), mock(StatusHistoryService.class), access);

    private ClipProject inWorkspace(String projectId, String workspaceId) {
        Workspace workspace = Workspace.builder().name(workspaceId).build();
        workspace.setId(workspaceId);
        ClipProject project = ClipProject.builder().title(projectId).workspace(workspace)
            .owner(User.builder().fullName("Writer").build())
            .scriptStatus(ScriptStatus.IN_REVIEW).build();
        project.setId(projectId);
        when(versions.findFirstByProjectIdAndIsCurrentTrue(projectId)).thenReturn(Optional.of(ScriptVersion.builder().build()));
        return project;
    }

    @Test
    @DisplayName("the review queue only lists scripts from workspaces where the user reviews")
    void listsOnlyWorkspacesWhereTheUserReviews() {
        List<ClipProject> waiting = List.of(
            inWorkspace("physics-script", "physics"), inWorkspace("chemistry-script", "chemistry"));
        when(projects.findByScriptStatusOrderByCreatedAtDesc(ScriptStatus.IN_REVIEW)).thenReturn(waiting);
        when(access.hasWorkspaceRole("physics", "teacher", "REVIEWER")).thenReturn(true);
        when(access.hasWorkspaceRole("chemistry", "teacher", "REVIEWER")).thenReturn(false);

        assertThat(service.findReviewQueue("teacher")).extracting("id").containsExactly("physics-script");
    }

    @Test
    @DisplayName("after leaving a workspace, the writer no longer sees their own scripts from it")
    void leavingAWorkspaceHidesOwnScripts() {
        List<ClipProject> owned = List.of(
            inWorkspace("physics-script", "physics"), inWorkspace("chemistry-script", "chemistry"));
        when(projects.findByOwnerIdOrderByCreatedAtDesc("teacher")).thenReturn(owned);
        when(access.canRead("physics", "teacher")).thenReturn(true);
        when(access.canRead("chemistry", "teacher")).thenReturn(false);

        assertThat(service.findByOwner("teacher")).extracting("id").containsExactly("physics-script");
    }
}
