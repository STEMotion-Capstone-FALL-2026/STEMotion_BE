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
 * Members are assigned directly by administrators or workspace leaders without email tokens.
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

        User leader = userRepository.findById(request.leaderUserId())
            .orElseThrow(() -> ApiException.notFound("User", request.leaderUserId()));

        if (leader.getStatus() != User.UserStatus.ACTIVE) {
            throw ApiException.badRequest("Selected workspace leader must be an active user");
        }

        Workspace workspace = workspaceRepository.save(Workspace.builder()
            .name(request.name())
            .department(request.department())
            .createdBy(creator)
            .build());

        User.UserRole leaderRole = request.leaderRole() != null ? request.leaderRole() : User.UserRole.WRITER;
        if (leaderRole == User.UserRole.ADMIN) {
            leaderRole = User.UserRole.WRITER;
        }

        memberRepository.save(WorkspaceMember.builder()
            .workspace(workspace)
            .user(leader)
            .role(leaderRole)
            .isLeader(true)
            .status(MemberStatus.ACTIVE)
            .build());

        return toResponse(workspace);
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

    @Transactional
    public MemberResponse addMember(String workspaceId, AddMemberRequest request) {
        Workspace workspace = getOrThrow(workspaceId);
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> ApiException.notFound("User", request.userId()));

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw ApiException.badRequest("User is not active and cannot be added to a workspace");
        }

        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId())) {
            throw ApiException.conflict("This user is already a member of this workspace");
        }

        User.UserRole memberRole = request.role();
        if (memberRole == User.UserRole.ADMIN) {
            memberRole = User.UserRole.WRITER;
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

    @Transactional
    public MemberResponse updateMember(String workspaceId, String memberId, UpdateMemberRequest request) {
        WorkspaceMember member = memberOrThrow(workspaceId, memberId);

        boolean isDemotingLeader = member.isLeader() && Boolean.FALSE.equals(request.leader());
        boolean isDeactivatingLeader = member.isLeader() && request.status() == MemberStatus.INACTIVE;

        if ((isDemotingLeader || isDeactivatingLeader) && member.getStatus() == MemberStatus.ACTIVE) {
            long activeLeaders = memberRepository.countByWorkspaceIdAndIsLeaderTrueAndStatus(workspaceId, MemberStatus.ACTIVE);
            if (activeLeaders <= 1) {
                throw ApiException.badRequest("Cannot remove or deactivate the last active leader of the workspace");
            }
        }

        if (request.role() != null) {
            User.UserRole newRole = request.role() == User.UserRole.ADMIN ? User.UserRole.WRITER : request.role();
            member.setRole(newRole);
        }
        if (request.status() != null) {
            member.setStatus(request.status());
        }
        if (request.leader() != null) {
            member.setLeader(request.leader());
        }

        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public void removeMember(String workspaceId, String memberId) {
        WorkspaceMember member = memberOrThrow(workspaceId, memberId);
        if (member.isLeader() && member.getStatus() == MemberStatus.ACTIVE) {
            long activeLeaders = memberRepository.countByWorkspaceIdAndIsLeaderTrueAndStatus(workspaceId, MemberStatus.ACTIVE);
            if (activeLeaders <= 1) {
                throw ApiException.badRequest("Cannot delete the last active leader of the workspace");
            }
        }
        memberRepository.delete(member);
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
