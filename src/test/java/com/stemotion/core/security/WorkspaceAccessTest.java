package com.stemotion.core.security;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceAccessTest {
    final UserRepository users = mock(UserRepository.class);
    final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    final ClipProjectRepository projects = mock(ClipProjectRepository.class);
    final RenderJobRepository jobs = mock(RenderJobRepository.class);
    final WorkspaceAccess access = new WorkspaceAccess(users, members, projects, jobs, mock(com.stemotion.domain.review.repository.FeedbackCommentRepository.class));

    void seat(User.UserRole role, MemberStatus status, boolean isLeader) {
        var workspace = new Workspace();
        workspace.setId("one");
        when(members.findByUserId("writer")).thenReturn(List.of(WorkspaceMember.builder()
            .workspace(workspace).role(role).status(status).isLeader(isLeader).build()));
    }

    @Test
    void membersCannotReadAnotherWorkspace() {
        seat(User.UserRole.WRITER, MemberStatus.ACTIVE, false);
        assertThat(access.canRead("one", "writer")).isTrue();
        assertThat(access.canRead("two", "writer")).isFalse();
        assertThatThrownBy(() -> access.requireWorkspace("two", "writer", false)).isInstanceOf(ApiException.class);
    }

    @Test
    void inactiveMemberIsNotActiveAccess() {
        seat(User.UserRole.WRITER, MemberStatus.INACTIVE, false);
        assertThat(access.canRead("one", "writer")).isFalse();
    }

    @Test
    void workspaceLeaderCanManageWithoutGlobalAdminRole() {
        seat(User.UserRole.WRITER, MemberStatus.ACTIVE, true);
        assertThatCode(() -> access.requireWorkspace("one", "writer", true)).doesNotThrowAnyException();
    }

    @Test
    void ordinaryMemberCannotManageSeats() {
        seat(User.UserRole.WRITER, MemberStatus.ACTIVE, false);
        assertThatThrownBy(() -> access.requireWorkspace("one", "writer", true)).isInstanceOf(ApiException.class);
    }

    @Test
    void globalAdminCanAccessWorkspace() {
        when(users.findById("admin")).thenReturn(Optional.of(User.builder().role(User.UserRole.ADMIN).build()));
        assertThatCode(() -> access.requireWorkspace("one", "admin", true)).doesNotThrowAnyException();
    }

    @Test
    void anyWorkspaceWriterCanEditDraftEvenIfNotOriginalOwner() {
        seat(User.UserRole.WRITER, MemberStatus.ACTIVE, false);
        var workspace = new Workspace();
        workspace.setId("one");
        var otherOwner = new User();
        otherOwner.setId("other");
        when(projects.findById("project")).thenReturn(Optional.of(ClipProject.builder()
            .workspace(workspace).owner(otherOwner).build()));

        // Any active WRITER in the workspace can edit draft
        assertThatCode(() -> access.requireProject("project", "writer", true)).doesNotThrowAnyException();
        assertThatCode(() -> access.requireProject("project", "writer", false)).doesNotThrowAnyException();
    }

    @Test
    void nonWriterCannotEditDraft() {
        seat(User.UserRole.REVIEWER, MemberStatus.ACTIVE, false);
        var workspace = new Workspace();
        workspace.setId("one");
        var otherOwner = new User();
        otherOwner.setId("other");
        when(projects.findById("project")).thenReturn(Optional.of(ClipProject.builder()
            .workspace(workspace).owner(otherOwner).build()));

        // Reviewer can read, but cannot edit draft
        assertThatCode(() -> access.requireProject("project", "writer", false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> access.requireProject("project", "writer", true)).isInstanceOf(ApiException.class);
    }

    @Test
    void requireAiAccessPassesForWriterOrAdmin() {
        seat(User.UserRole.WRITER, MemberStatus.ACTIVE, false);
        assertThatCode(() -> access.requireAiAccess("writer")).doesNotThrowAnyException();

        when(users.findById("admin")).thenReturn(Optional.of(User.builder().role(User.UserRole.ADMIN).build()));
        assertThatCode(() -> access.requireAiAccess("admin")).doesNotThrowAnyException();

        when(members.findByUserId("other")).thenReturn(List.of());
        when(users.findById("other")).thenReturn(Optional.of(User.builder().role(User.UserRole.WRITER).build()));
        assertThatThrownBy(() -> access.requireAiAccess("other")).isInstanceOf(ApiException.class);
    }

    @Test
    void producersUseAiForNarrationAndCaptionsButReviewersDoNot() {
        seat(User.UserRole.PRODUCER, MemberStatus.ACTIVE, false);
        assertThatCode(() -> access.requireAiAccess("writer")).doesNotThrowAnyException();
        seat(User.UserRole.REVIEWER, MemberStatus.ACTIVE, false);
        assertThatThrownBy(() -> access.requireAiAccess("writer")).isInstanceOf(ApiException.class);
    }

    @Test
    void roleAnywhereIgnoresTheAccountRoleAndInactiveSeats() {
        // Account role says WRITER, but the only seat is inactive: no access.
        when(users.findById("writer")).thenReturn(Optional.of(User.builder().role(User.UserRole.WRITER).build()));
        seat(User.UserRole.WRITER, MemberStatus.INACTIVE, false);
        assertThat(access.hasRoleAnywhere("writer", "WRITER", "PRODUCER")).isFalse();
        // Account role says REVIEWER, but the seat is PRODUCER: access.
        when(users.findById("writer")).thenReturn(Optional.of(User.builder().role(User.UserRole.REVIEWER).build()));
        seat(User.UserRole.PRODUCER, MemberStatus.ACTIVE, false);
        assertThat(access.hasRoleAnywhere("writer", "WRITER", "PRODUCER")).isTrue();
    }
}
