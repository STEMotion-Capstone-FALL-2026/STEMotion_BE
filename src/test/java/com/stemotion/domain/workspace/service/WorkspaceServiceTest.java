package com.stemotion.domain.workspace.service;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.AddMemberRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.UpdateMemberRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.WorkspaceRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.WorkspaceResponse;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkspaceServiceTest {

    final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    final ClipProjectRepository projects = mock(ClipProjectRepository.class);
    final UserRepository users = mock(UserRepository.class);

    final WorkspaceService service = new WorkspaceService(workspaces, members, projects, users);

    User admin;
    User leader;
    User teacher;
    Workspace workspace;

    @BeforeEach
    void setup() {
        admin = User.builder().fullName("Admin").email("admin@test.invalid").role(User.UserRole.ADMIN).status(User.UserStatus.ACTIVE).build();
        admin.setId("admin-id");

        leader = User.builder().fullName("Leader").email("leader@test.invalid").role(User.UserRole.WRITER).status(User.UserStatus.ACTIVE).build();
        leader.setId("leader-id");

        teacher = User.builder().fullName("Teacher").email("teacher@test.invalid").role(User.UserRole.WRITER).status(User.UserStatus.ACTIVE).build();
        teacher.setId("teacher-id");

        workspace = Workspace.builder().name("Math Department").department("Math").createdBy(admin).build();
        workspace.setId("ws-1");

        when(workspaces.findById("ws-1")).thenReturn(Optional.of(workspace));
        when(workspaces.save(any(Workspace.class))).thenAnswer(inv -> {
            Workspace w = inv.getArgument(0);
            if (w.getId() == null) w.setId("ws-1");
            return w;
        });

        when(users.findById("admin-id")).thenReturn(Optional.of(admin));
        when(users.findById("leader-id")).thenReturn(Optional.of(leader));
        when(users.findById("teacher-id")).thenReturn(Optional.of(teacher));

        when(members.save(any(WorkspaceMember.class))).thenAnswer(inv -> {
            WorkspaceMember m = inv.getArgument(0);
            if (m.getId() == null) m.setId("mem-id");
            return m;
        });
    }

    @Test
    void createWorkspaceDirectlySeatsLeaderAsActive() {
        var req = new WorkspaceRequest("Math Department", "Math", "leader-id", User.UserRole.WRITER);
        WorkspaceResponse res = service.create(req, "admin-id");

        assertThat(res.id()).isEqualTo("ws-1");
        var captor = org.mockito.ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(members).save(captor.capture());

        WorkspaceMember leaderSeat = captor.getValue();
        assertThat(leaderSeat.getUser()).isEqualTo(leader);
        assertThat(leaderSeat.isLeader()).isTrue();
        assertThat(leaderSeat.getRole()).isEqualTo(User.UserRole.WRITER);
        assertThat(leaderSeat.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void addMemberDirectlyCreatesActiveMember() {
        when(members.existsByWorkspaceIdAndUserId("ws-1", "teacher-id")).thenReturn(false);

        var req = new AddMemberRequest("teacher-id", User.UserRole.REVIEWER, false);
        var res = service.addMember("ws-1", req);

        assertThat(res.userId()).isEqualTo("teacher-id");
        assertThat(res.role()).isEqualTo(User.UserRole.REVIEWER);
        assertThat(res.isLeader()).isFalse();
        assertThat(res.status()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void addExistingMemberThrowsConflict() {
        when(members.existsByWorkspaceIdAndUserId("ws-1", "teacher-id")).thenReturn(true);

        var req = new AddMemberRequest("teacher-id", User.UserRole.WRITER, false);
        assertThatThrownBy(() -> service.addMember("ws-1", req))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("already a member");
    }

    @Test
    void removeLastActiveLeaderThrowsBadRequest() {
        WorkspaceMember leaderSeat = WorkspaceMember.builder()
            .workspace(workspace)
            .user(leader)
            .role(User.UserRole.WRITER)
            .isLeader(true)
            .status(MemberStatus.ACTIVE)
            .build();
        leaderSeat.setId("leader-seat-id");

        when(members.findById("leader-seat-id")).thenReturn(Optional.of(leaderSeat));
        when(members.countByWorkspaceIdAndIsLeaderTrueAndStatus("ws-1", MemberStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.removeMember("ws-1", "leader-seat-id"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("last active leader");
    }

    @Test
    void demoteLastActiveLeaderThrowsBadRequest() {
        WorkspaceMember leaderSeat = WorkspaceMember.builder()
            .workspace(workspace)
            .user(leader)
            .role(User.UserRole.WRITER)
            .isLeader(true)
            .status(MemberStatus.ACTIVE)
            .build();
        leaderSeat.setId("leader-seat-id");

        when(members.findById("leader-seat-id")).thenReturn(Optional.of(leaderSeat));
        when(members.countByWorkspaceIdAndIsLeaderTrueAndStatus("ws-1", MemberStatus.ACTIVE)).thenReturn(1L);

        var req = new UpdateMemberRequest(null, null, false);
        assertThatThrownBy(() -> service.updateMember("ws-1", "leader-seat-id", req))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("last active leader");
    }

    @Test
    void findCandidatesReturnsOnlyActiveUsersNotInWorkspace() {
        when(users.findCandidatesForWorkspace("ws-1")).thenReturn(List.of(teacher));

        var candidates = service.findCandidates("ws-1");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo("teacher-id");
        assertThat(candidates.get(0).email()).isEqualTo("teacher@test.invalid");
    }

    @Test
    void anAdministratorListsEveryWorkspaceAndOthersOnlyTheirOwn() {
        Workspace other = Workspace.builder().name("Physics Department").department("Physics").createdBy(admin).build();
        other.setId("ws-2");
        when(workspaces.findAllByOrderByNameAsc()).thenReturn(List.of(workspace, other));
        WorkspaceMember seat = WorkspaceMember.builder().workspace(workspace).user(teacher)
            .role(User.UserRole.WRITER).status(MemberStatus.ACTIVE).build();
        when(members.findWithWorkspaceByUserId("teacher-id")).thenReturn(List.of(seat));
        when(members.findWithWorkspaceByUserId("admin-id")).thenReturn(List.of());
        when(members.countByWorkspaceIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[] {"ws-1", 3L}));
        when(projects.countByWorkspaceIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[] {"ws-2", 5L}));

        var visible = service.findVisible("admin-id");
        assertThat(visible).extracting(WorkspaceResponse::id).containsExactly("ws-1", "ws-2");
        assertThat(visible).extracting(WorkspaceResponse::membersCount).containsExactly(3L, 0L);
        assertThat(visible).extracting(WorkspaceResponse::activeProjects).containsExactly(0L, 5L);
        var mine = service.findVisible("teacher-id");
        assertThat(mine).extracting(WorkspaceResponse::id).containsExactly("ws-1");
        assertThat(mine.get(0).myRole()).isEqualTo(User.UserRole.WRITER);
        // Counts are two grouped queries, never one count per workspace.
        verify(members, never()).countByWorkspaceId(org.mockito.ArgumentMatchers.anyString());
        verify(projects, never()).countByWorkspaceId(org.mockito.ArgumentMatchers.anyString());
    }
}
