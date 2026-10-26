package com.stemotion.domain.workspace.service;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.AddMemberRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.CandidateUserResponse;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.MemberResponse;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.UpdateMemberRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.WorkspaceRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.WorkspaceResponse;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Subject departments ("tổ bộ môn") and their memberships.
 *
 * <p>Master update plan M10/M11/M15: an administrator creates a workspace with at least three
 * people covering WRITER, REVIEWER and PRODUCER, and is the only one who adds or removes members,
 * changes a member's status or appoints a leader. A leader assigns roles inside the workspace.
 * No change may leave a covered production role without an active member.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ClipProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> findAll() {
        List<Workspace> workspaces = workspaceRepository.findAllByOrderByNameAsc();
        Counts counts = counts(workspaces.stream().map(Workspace::getId).toList());
        return workspaces.stream().map(workspace -> counts.response(workspace, null, null)).toList();
    }

    /**
     * The user's active seats. Member and project counts are two grouped queries for all of them
     * (DB performance plan 2.3), not two counts per workspace.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> findMine(String userId) {
        List<WorkspaceMember> seats = memberRepository.findWithWorkspaceByUserId(userId).stream()
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .toList();
        Counts counts = counts(seats.stream().map(member -> member.getWorkspace().getId()).toList());
        return seats.stream()
            .map(member -> counts.response(member.getWorkspace(), member.getRole(), member.isLeader()))
            .toList();
    }

    /** Member and project counts of several workspaces, read with one grouped query each. */
    private Counts counts(java.util.Collection<String> workspaceIds) {
        if (workspaceIds.isEmpty()) return new Counts(java.util.Map.of(), java.util.Map.of());
        return new Counts(toMap(memberRepository.countByWorkspaceIds(workspaceIds)),
            toMap(projectRepository.countByWorkspaceIds(workspaceIds)));
    }

    private static java.util.Map<String, Long> toMap(List<Object[]> rows) {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (Object[] row : rows) map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        return map;
    }

    private record Counts(java.util.Map<String, Long> members, java.util.Map<String, Long> projects) {
        WorkspaceResponse response(Workspace workspace, User.UserRole myRole, Boolean isLeader) {
            return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getDepartment(),
                members.getOrDefault(workspace.getId(), 0L), projects.getOrDefault(workspace.getId(), 0L),
                myRole, isLeader);
        }
    }

    /**
     * Workspaces a user can open: an administrator manages every department, so sees all of them
     * (with their own seat where they have one); anyone else sees their active memberships.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> findVisible(String userId) {
        boolean admin = userRepository.findById(userId)
            .map(user -> user.getRole() == User.UserRole.ADMIN).orElse(false);
        if (!admin) return findMine(userId);
        var seats = memberRepository.findWithWorkspaceByUserId(userId).stream()
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .collect(java.util.stream.Collectors.toMap(member -> member.getWorkspace().getId(), member -> member, (a, b) -> a));
        List<Workspace> workspaces = workspaceRepository.findAllByOrderByNameAsc();
        Counts counts = counts(workspaces.stream().map(Workspace::getId).toList());
        return workspaces.stream().map(workspace -> {
            WorkspaceMember seat = seats.get(workspace.getId());
            return counts.response(workspace, seat == null ? null : seat.getRole(), seat == null ? null : seat.isLeader());
        }).toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse findById(String id) {
        return toResponse(getOrThrow(id));
    }

    /**
     * Admin creates a workspace and immediately seats the designated leader.
     */
    @Transactional
    public WorkspaceResponse create(WorkspaceRequest request, String currentUserId) {
        User creator = userRepository.findById(currentUserId)
            .orElseThrow(() -> ApiException.notFound("User", currentUserId));

        List<AddMemberRequest> seats = new java.util.ArrayList<>();
        seats.add(new AddMemberRequest(request.leaderUserId(),
            request.leaderRole() != null ? request.leaderRole() : User.UserRole.WRITER, true));
        if (request.members() != null) seats.addAll(request.members());

        // Everything is checked before the workspace row exists, and it is one transaction,
        // so a refused team never leaves a workspace behind.
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<User> people = new java.util.ArrayList<>();
        for (AddMemberRequest seat : seats) {
            if (!seen.add(seat.userId())) {
                throw ApiException.badRequest("User " + seat.userId() + " is listed more than once");
            }
            productionRole(seat.role());
            people.add(activeUser(seat.userId()));
        }
        requireFullTeam(seats.stream().map(AddMemberRequest::role).toList());

        Workspace workspace = workspaceRepository.save(Workspace.builder()
            .name(request.name())
            .department(request.department())
            .createdBy(creator)
            .build());

        for (int i = 0; i < seats.size(); i++) {
            memberRepository.save(WorkspaceMember.builder()
                .workspace(workspace)
                .user(people.get(i))
                .role(seats.get(i).role())
                .isLeader(Boolean.TRUE.equals(seats.get(i).leader()))
                .status(MemberStatus.ACTIVE)
                .build());
        }

        return toResponse(workspace);
    }

    /** Production roles a workspace must cover (master update plan M11). */
    public static final java.util.Set<User.UserRole> PRODUCTION_ROLES =
        java.util.EnumSet.of(User.UserRole.WRITER, User.UserRole.REVIEWER, User.UserRole.PRODUCER);
    public static final int MIN_MEMBERS = 3;

    static void requireFullTeam(List<User.UserRole> roles) {
        if (roles.size() < MIN_MEMBERS) {
            throw ApiException.badRequest("A workspace needs at least " + MIN_MEMBERS
                + " different members (WRITER, REVIEWER and PRODUCER); got " + roles.size());
        }
        java.util.Set<User.UserRole> missing = java.util.EnumSet.copyOf(PRODUCTION_ROLES);
        missing.removeAll(roles);
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("A workspace needs at least one WRITER, REVIEWER and PRODUCER; missing "
                + missing);
        }
    }

    private static User.UserRole productionRole(User.UserRole role) {
        if (role == null) throw ApiException.badRequest("Every member needs a role");
        if (!PRODUCTION_ROLES.contains(role)) {
            throw ApiException.badRequest("A workspace role must be WRITER, REVIEWER or PRODUCER, not " + role);
        }
        return role;
    }

    private User activeUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw ApiException.badRequest("User " + user.getFullName() + " is not active and cannot join a workspace");
        }
        return user;
    }

    private boolean isAdmin(String userId) {
        return userId != null && userRepository.findById(userId)
            .map(user -> user.getRole() == User.UserRole.ADMIN).orElse(false);
    }

    /**
     * Refuses a change that would leave a production role, covered by an active member now,
     * without one (master update plan D10). A workspace that already lacks a role, from before
     * this rule, can still be changed so it can be completed.
     */
    private void requireCoverageKept(String workspaceId, WorkspaceMember changed, User.UserRole newRole,
                                     MemberStatus newStatus, boolean removed) {
        java.util.Set<User.UserRole> before = java.util.EnumSet.noneOf(User.UserRole.class);
        java.util.Set<User.UserRole> after = java.util.EnumSet.noneOf(User.UserRole.class);
        for (WorkspaceMember seat : memberRepository.findWithUserByWorkspaceId(workspaceId)) {
            if (seat.getStatus() == MemberStatus.ACTIVE) before.add(seat.getRole());
            boolean isChanged = seat.getId().equals(changed.getId());
            if (isChanged && removed) continue;
            if ((isChanged ? newStatus : seat.getStatus()) == MemberStatus.ACTIVE) {
                after.add(isChanged ? newRole : seat.getRole());
            }
        }
        before.retainAll(PRODUCTION_ROLES);
        before.removeAll(after);
        if (!before.isEmpty()) {
            throw ApiException.badRequest("This change would leave the workspace without an active "
                + before + "; give that role to another member first");
        }
    }

    @Transactional
    public WorkspaceResponse update(String id, WorkspaceRequest request) {
        Workspace workspace = getOrThrow(id);
        workspace.setName(request.name());
        workspace.setDepartment(request.department());
        return toResponse(workspaceRepository.save(workspace));
    }

    @Transactional
    public void delete(String id) {
        if (!workspaceRepository.existsById(id)) {
            throw ApiException.notFound("Workspace", id);
        }
        workspaceRepository.deleteById(id);
    }

    // ------------------------------------------------------------- membership

    @Transactional(readOnly = true)
    public List<MemberResponse> findMembers(String workspaceId) {
        getOrThrow(workspaceId);
        return memberRepository.findWithUserByWorkspaceId(workspaceId)
            .stream()
            .map(MemberResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateUserResponse> findCandidates(String workspaceId) {
        getOrThrow(workspaceId);
        return userRepository.findCandidatesForWorkspace(workspaceId)
            .stream()
            .map(CandidateUserResponse::from)
            .toList();
    }

    /** Administrators only (controller); a leader assigns roles but does not add people (M15). */
    @Transactional
    public MemberResponse addMember(String workspaceId, AddMemberRequest request) {
        Workspace workspace = lockOrThrow(workspaceId);
        User.UserRole memberRole = productionRole(request.role());
        User user = activeUser(request.userId());

        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId())) {
            throw ApiException.conflict("This user is already a member of this workspace");
        }

        WorkspaceMember member = memberRepository.save(WorkspaceMember.builder()
            .workspace(workspace)
            .user(user)
            .role(memberRole)
            .isLeader(Boolean.TRUE.equals(request.leader()))
            .status(MemberStatus.ACTIVE)
            .build());

        return MemberResponse.from(member);
    }

    /**
     * A leader may change a member's role; status and the leader designation are kept to
     * administrators (M15), so a leader cannot deactivate someone instead of removing them.
     * Fields sent unchanged are not a change, so a client may send the whole member back.
     */
    @Transactional
    public MemberResponse updateMember(String workspaceId, String memberId, UpdateMemberRequest request,
                                       String actorId) {
        lockOrThrow(workspaceId);
        WorkspaceMember member = memberOrThrow(workspaceId, memberId);

        boolean statusChange = request.status() != null && request.status() != member.getStatus();
        boolean leaderChange = request.leader() != null && request.leader() != member.isLeader();
        if ((statusChange || leaderChange) && !isAdmin(actorId)) {
            throw ApiException.forbidden("Only a system administrator can change a member's status or leader "
                + "designation; a workspace leader assigns roles");
        }
        User.UserRole newRole = request.role() == null ? member.getRole() : productionRole(request.role());

        boolean isDemotingLeader = member.isLeader() && Boolean.FALSE.equals(request.leader());
        boolean isDeactivatingLeader = member.isLeader() && request.status() == MemberStatus.INACTIVE;

        if ((isDemotingLeader || isDeactivatingLeader) && member.getStatus() == MemberStatus.ACTIVE) {
            long activeLeaders = memberRepository.countByWorkspaceIdAndIsLeaderTrueAndStatus(workspaceId, MemberStatus.ACTIVE);
            if (activeLeaders <= 1) {
                throw ApiException.badRequest("Cannot remove or deactivate the last active leader of the workspace");
            }
        }

        requireCoverageKept(workspaceId, member, newRole,
            request.status() != null ? request.status() : member.getStatus(), false);

        member.setRole(newRole);
        if (request.status() != null) {
            member.setStatus(request.status());
        }
        if (request.leader() != null) {
            member.setLeader(request.leader());
        }

        return MemberResponse.from(memberRepository.save(member));
    }

    /** Administrators only (controller). */
    @Transactional
    public void removeMember(String workspaceId, String memberId) {
        lockOrThrow(workspaceId);
        WorkspaceMember member = memberOrThrow(workspaceId, memberId);
        if (member.isLeader() && member.getStatus() == MemberStatus.ACTIVE) {
            long activeLeaders = memberRepository.countByWorkspaceIdAndIsLeaderTrueAndStatus(workspaceId, MemberStatus.ACTIVE);
            if (activeLeaders <= 1) {
                throw ApiException.badRequest("Cannot delete the last active leader of the workspace");
            }
        }
        requireCoverageKept(workspaceId, member, member.getRole(), member.getStatus(), true);
        memberRepository.delete(member);
    }

    private Workspace lockOrThrow(String id) {
        return workspaceRepository.lockById(id).orElseThrow(() -> ApiException.notFound("Workspace", id));
    }

    @Transactional(readOnly = true)
    public Workspace getOrThrow(String id) {
        return workspaceRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Workspace", id));
    }

    private WorkspaceMember memberOrThrow(String workspaceId, String memberId) {
        WorkspaceMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> ApiException.notFound("WorkspaceMember", memberId));
        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw ApiException.badRequest("Member " + memberId + " is not part of workspace " + workspaceId);
        }
        return member;
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(
            workspace.getId(),
            workspace.getName(),
            workspace.getDepartment(),
            memberRepository.countByWorkspaceId(workspace.getId()),
            projectRepository.countByWorkspaceId(workspace.getId()),
            null,
            null
        );
    }
}
