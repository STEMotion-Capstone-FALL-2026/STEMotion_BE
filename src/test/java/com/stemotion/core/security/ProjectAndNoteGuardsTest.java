package com.stemotion.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.review.entity.FeedbackComment;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.review.service.FeedbackService;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.AddMemberRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.UpdateMemberRequest;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards found missing when the backend was exercised end to end: archiving a
 * project, handling QA notes, and the leader flag the front end sends.
 */
class ProjectAndNoteGuardsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final ClipProjectRepository projects = mock(ClipProjectRepository.class);
    private final FeedbackCommentRepository comments = mock(FeedbackCommentRepository.class);
    private final WorkspaceAccess access =
        new WorkspaceAccess(users, members, projects, mock(RenderJobRepository.class), comments);

    private final Workspace physics = workspace("physics");
    private final ClipProject project = project();

    private static Workspace workspace(String id) {
        Workspace w = Workspace.builder().name(id).build();
        w.setId(id);
        return w;
    }

    private static User user(String id, User.UserRole role) {
        User u = User.builder().fullName(id).email(id + "@x").role(role).build();
        u.setId(id);
        return u;
    }

    private ClipProject project() {
        ClipProject p = ClipProject.builder().title("Ôm").workspace(physics).owner(user("owner", User.UserRole.WRITER)).build();
        p.setId("project");
        return p;
    }

    private void seat(String userId, User.UserRole role, boolean leader) {
        when(users.findById(userId)).thenReturn(Optional.of(user(userId, User.UserRole.WRITER)));
        when(members.findByUserId(userId)).thenReturn(List.of(WorkspaceMember.builder()
            .workspace(physics).role(role).status(MemberStatus.ACTIVE).isLeader(leader).build()));
    }

    @Test
    @DisplayName("only the owner, the workspace leader or an admin archives a project")
    void archiveIsKeptToOwnerLeaderAndAdmin() {
        when(projects.findById("project")).thenReturn(Optional.of(project));
        seat("owner", User.UserRole.WRITER, false);
        seat("leader", User.UserRole.REVIEWER, true);
        seat("writer", User.UserRole.WRITER, false);
        when(users.findById("admin")).thenReturn(Optional.of(user("admin", User.UserRole.ADMIN)));

        assertThat(access.canArchiveProject("project", "owner")).isTrue();
        assertThat(access.canArchiveProject("project", "leader")).isTrue();
        assertThat(access.canArchiveProject("project", "admin")).isTrue();
        assertThat(access.canArchiveProject("project", "writer")).isFalse();
    }

    @Test
    @DisplayName("QA note roles are checked through the note's project")
    void noteRolesFollowTheProject() {
        when(projects.findById("project")).thenReturn(Optional.of(project));
        when(comments.findById("note")).thenReturn(Optional.of(FeedbackComment.builder().project(project).build()));
        seat("producer", User.UserRole.PRODUCER, false);
        seat("writer", User.UserRole.WRITER, false);

        assertThat(access.hasCommentRole("note", "producer", "PRODUCER", "REVIEWER")).isTrue();
        assertThat(access.hasCommentRole("note", "writer", "PRODUCER", "REVIEWER")).isFalse();
        // A missing note is left to the service, which answers 404.
        assertThat(access.hasCommentRole("missing", "writer", "REVIEWER")).isTrue();
    }

    @Test
    @DisplayName("only the author or an admin deletes a QA note")
    void notesAreDeletedByTheirAuthorOnly() {
        AuthService auth = mock(AuthService.class);
        FeedbackService service = new FeedbackService(comments, null, projects, null, auth, null, null);
        FeedbackComment note = FeedbackComment.builder().project(project).author(user("reviewer", User.UserRole.WRITER)).build();
        when(comments.findById("note")).thenReturn(Optional.of(note));
        when(auth.findUserOrThrow("producer")).thenReturn(user("producer", User.UserRole.WRITER));
        when(auth.findUserOrThrow("reviewer")).thenReturn(user("reviewer", User.UserRole.WRITER));
        when(auth.findUserOrThrow("admin")).thenReturn(user("admin", User.UserRole.ADMIN));

        assertThatThrownBy(() -> service.delete("note", "producer")).isInstanceOf(ApiException.class)
            .hasMessageContaining("author");
        verify(comments, never()).delete(note);
        assertThatCode(() -> service.delete("note", "reviewer")).doesNotThrowAnyException();
        assertThatCode(() -> service.delete("note", "admin")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the leader flag is read whether the client calls it leader or isLeader")
    void leaderFlagAcceptsBothNames() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertThat(json.readValue("{\"userId\":\"u\",\"role\":\"WRITER\",\"isLeader\":true}", AddMemberRequest.class).leader()).isTrue();
        assertThat(json.readValue("{\"userId\":\"u\",\"role\":\"WRITER\",\"leader\":true}", AddMemberRequest.class).leader()).isTrue();
        assertThat(json.readValue("{\"isLeader\":false}", UpdateMemberRequest.class).leader()).isFalse();
    }
}
