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

    private static com.stemotion.domain.project.repository.ProjectSummaryRow row(String id) {
        return new com.stemotion.domain.project.repository.ProjectSummaryRow(id, id, null, "10", 60,
            ScriptStatus.IN_REVIEW, null, "Writer", 4L, null);
    }

    @Test
    @DisplayName("the review queue asks for scripts in workspaces where the user is an active reviewer")
    void listsOnlyWorkspacesWhereTheUserReviews() {
        when(projects.findReviewQueueSummaries("teacher", false, ScriptStatus.IN_REVIEW,
                com.stemotion.core.enums.StemEnums.MemberStatus.ACTIVE, User.UserRole.REVIEWER))
            .thenReturn(List.of(row("physics-script")));

        assertThat(service.findReviewQueue("teacher")).extracting("id").containsExactly("physics-script");
        assertThat(service.findReviewQueue("teacher").get(0).sceneCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("own scripts are listed only from workspaces the writer still belongs to (admins see all)")
    void leavingAWorkspaceHidesOwnScripts() {
        when(projects.findSummariesByOwner("teacher", false, com.stemotion.core.enums.StemEnums.MemberStatus.ACTIVE))
            .thenReturn(List.of(row("physics-script")));
        when(access.isAdmin("admin")).thenReturn(true);
        when(projects.findSummariesByOwner("admin", true, com.stemotion.core.enums.StemEnums.MemberStatus.ACTIVE))
            .thenReturn(List.of(row("physics-script"), row("chemistry-script")));

        assertThat(service.findByOwner("teacher")).extracting("id").containsExactly("physics-script");
        assertThat(service.findByOwner("admin")).extracting("id").containsExactly("physics-script", "chemistry-script");
    }
}
