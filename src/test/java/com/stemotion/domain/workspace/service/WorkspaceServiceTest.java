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
import static org.mockito.ArgumentMatchers.argThat;
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
    User reviewer;
    User producer;
    Workspace workspace;

    @BeforeEach
    void setup() {
        admin = User.builder().fullName("Admin").email("admin@test.invalid").role(User.UserRole.ADMIN).status(User.UserStatus.ACTIVE).build();
        admin.setId("admin-id");

        leader = User.builder().fullName("Leader").email("leader@test.invalid").role(User.UserRole.WRITER).status(User.UserStatus.ACTIVE).build();
        leader.setId("leader-id");

        teacher = User.builder().fullName("Teacher").email("teacher@test.invalid").role(User.UserRole.WRITER).status(User.UserStatus.ACTIVE).build();
        teacher.setId("teacher-id");

        reviewer = User.builder().fullName("Reviewer").email("reviewer@test.invalid").role(User.UserRole.REVIEWER).status(User.UserStatus.ACTIVE).build();
        reviewer.setId("reviewer-id");
        producer = User.builder().fullName("Producer").email("producer@test.invalid").role(User.UserRole.PRODUCER).status(User.UserStatus.ACTIVE).build();
        producer.setId("producer-id");

        workspace = Workspace.builder().name("Math Department").department("Math").createdBy(admin).build();
        workspace.setId("ws-1");

        when(workspaces.findById("ws-1")).thenReturn(Optional.of(workspace));
        when(workspaces.lockById("ws-1")).thenReturn(Optional.of(workspace));
        when(workspaces.save(any(Workspace.class))).thenAnswer(inv -> {
            Workspace w = inv.getArgument(0);
            if (w.getId() == null) w.setId("ws-1");
            return w;
        });

        when(users.findById("admin-id")).thenReturn(Optional.of(admin));
        when(users.findById("leader-id")).thenReturn(Optional.of(leader));
        when(users.findById("teacher-id")).thenReturn(Optional.of(teacher));
        when(users.findById("reviewer-id")).thenReturn(Optional.of(reviewer));
        when(users.findById("producer-id")).thenReturn(Optional.of(producer));

        when(members.save(any(WorkspaceMember.class))).thenAnswer(inv -> {
            WorkspaceMember m = inv.getArgument(0);
            if (m.getId() == null) m.setId("mem-id");
            return m;
        });
    }

    static AddMemberRequest seat(String userId, User.UserRole role) {
        return new AddMemberRequest(userId, role, false);
    }

    WorkspaceRequest team(AddMemberRequest... others) {
        return new WorkspaceRequest("Math Department", "Math", "leader-id", User.UserRole.WRITER, List.of(others));
    }

    @Test
    void createWorkspaceSeatsTheWholeTeamAsActive() {
        WorkspaceResponse res = service.create(team(seat("reviewer-id", User.UserRole.REVIEWER),
            seat("producer-id", User.UserRole.PRODUCER)), "admin-id");

        assertThat(res.id()).isEqualTo("ws-1");
        var captor = org.mockito.ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(members, times(3)).save(captor.capture());
        var seats = captor.getAllValues();
        assertThat(seats).extracting(WorkspaceMember::getUser).containsExactly(leader, reviewer, producer);
        assertThat(seats).extracting(WorkspaceMember::getRole)
            .containsExactly(User.UserRole.WRITER, User.UserRole.REVIEWER, User.UserRole.PRODUCER);
        assertThat(seats).extracting(WorkspaceMember::isLeader).containsExactly(true, false, false);
        assertThat(seats).allMatch(m -> m.getStatus() == MemberStatus.ACTIVE);
    }

    @Test
    void createRefusesATeamThatIsNotThreePeopleCoveringEveryRole() {
        assertThatThrownBy(() -> service.create(team(seat("reviewer-id", User.UserRole.REVIEWER)), "admin-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("at least 3");
        assertThatThrownBy(() -> service.create(team(seat("reviewer-id", User.UserRole.REVIEWER),
            seat("teacher-id", User.UserRole.WRITER)), "admin-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("missing [PRODUCER]");
        assertThatThrownBy(() -> service.create(team(seat("reviewer-id", User.UserRole.REVIEWER),
            seat("leader-id", User.UserRole.PRODUCER)), "admin-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("more than once");
        assertThatThrownBy(() -> service.create(team(seat("reviewer-id", User.UserRole.REVIEWER),
            seat("producer-id", User.UserRole.ADMIN)), "admin-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("not ADMIN");
        producer.setStatus(User.UserStatus.INACTIVE);
        assertThatThrownBy(() -> service.create(team(seat("reviewer-id", User.UserRole.REVIEWER),
            seat("producer-id", User.UserRole.PRODUCER)), "admin-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("not active");
        // Nothing is written for a refused team.
        verify(workspaces, never()).save(any());
        verify(members, never()).save(any());
    }

    @Test
    void addMemberRefusesTheAdminRoleInsteadOfTurningItIntoWriter() {
        assertThatThrownBy(() -> service.addMember("ws-1", new AddMemberRequest("teacher-id", User.UserRole.ADMIN, false)))
            .isInstanceOf(ApiException.class).hasMessageContaining("not ADMIN");
        verify(members, never()).save(any());
    }

    WorkspaceMember seatOf(String id, User user, User.UserRole role, boolean isLeader) {
        WorkspaceMember m = WorkspaceMember.builder().workspace(workspace).user(user).role(role)
            .isLeader(isLeader).status(MemberStatus.ACTIVE).build();
        m.setId(id);
        when(members.findById(id)).thenReturn(Optional.of(m));
        return m;
    }

    /** A covered workspace: leader WRITER, a REVIEWER and a PRODUCER, optionally a second writer. */
    List<WorkspaceMember> coveredTeam(boolean secondWriter) {
        var list = new java.util.ArrayList<>(List.of(
            seatOf("s-leader", leader, User.UserRole.WRITER, true),
            seatOf("s-reviewer", reviewer, User.UserRole.REVIEWER, false),
            seatOf("s-producer", producer, User.UserRole.PRODUCER, false)));
        if (secondWriter) list.add(seatOf("s-teacher", teacher, User.UserRole.WRITER, false));
        when(members.findWithUserByWorkspaceId("ws-1")).thenReturn(list);
        when(members.countByWorkspaceIdAndIsLeaderTrueAndStatus("ws-1", MemberStatus.ACTIVE)).thenReturn(1L);
        return list;
    }

    @Test
    void aLeaderAssignsRolesButCannotChangeStatusOrLeadership() {
        coveredTeam(true);
        var changed = service.updateMember("ws-1", "s-teacher", new UpdateMemberRequest(User.UserRole.PRODUCER, null, null), "leader-id");
        assertThat(changed.role()).isEqualTo(User.UserRole.PRODUCER);
        // Sending the unchanged status and flag back with the role is not a status change.
        service.updateMember("ws-1", "s-teacher", new UpdateMemberRequest(User.UserRole.WRITER, MemberStatus.ACTIVE, false), "leader-id");

        assertThatThrownBy(() -> service.updateMember("ws-1", "s-teacher",
            new UpdateMemberRequest(null, MemberStatus.INACTIVE, null), "leader-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("system administrator");
        assertThatThrownBy(() -> service.updateMember("ws-1", "s-teacher",
            new UpdateMemberRequest(null, null, true), "leader-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("system administrator");

        var deactivated = service.updateMember("ws-1", "s-teacher", new UpdateMemberRequest(null, MemberStatus.INACTIVE, null), "admin-id");
        assertThat(deactivated.status()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    void noChangeMayLeaveACoveredRoleWithoutAnActiveMember() {
        coveredTeam(false);
        assertThatThrownBy(() -> service.removeMember("ws-1", "s-reviewer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("without an active [REVIEWER]");
        assertThatThrownBy(() -> service.updateMember("ws-1", "s-producer",
            new UpdateMemberRequest(null, MemberStatus.INACTIVE, null), "admin-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("[PRODUCER]");
        assertThatThrownBy(() -> service.updateMember("ws-1", "s-leader",
            new UpdateMemberRequest(User.UserRole.REVIEWER, null, null), "leader-id"))
            .isInstanceOf(ApiException.class).hasMessageContaining("[WRITER]");
        verify(members, never()).delete(any());
        verify(members, never()).save(any());
    }

    @Test
    void aSecondMemberOfARoleCanLeaveAndAnIncompleteLegacyWorkspaceCanStillBeChanged() {
        coveredTeam(true);
        service.removeMember("ws-1", "s-teacher");
        verify(members).delete(argThat(m -> m.getId().equals("s-teacher")));

        // A workspace from before the rule, two writers and nobody else: the roles it never had do not
        // block a change, so it can be completed.
        WorkspaceMember first = seatOf("s-first", leader, User.UserRole.WRITER, true);
        WorkspaceMember second = seatOf("s-second", teacher, User.UserRole.WRITER, false);
        when(members.findWithUserByWorkspaceId("ws-1")).thenReturn(List.of(first, second));
        var res = service.updateMember("ws-1", "s-second", new UpdateMemberRequest(User.UserRole.PRODUCER, null, null), "leader-id");
        assertThat(res.role()).isEqualTo(User.UserRole.PRODUCER);
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
        assertThatThrownBy(() -> service.updateMember("ws-1", "leader-seat-id", req, "admin-id"))
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
