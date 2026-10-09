package com.stemotion.domain.workspace.service;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.InviteMemberRequest;
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
 * Subject departments and their membership. Inviting a teacher who has no
 * account yet records the email only; the seat activates when that email
 * registers and {@link #linkPendingInvitations(User)} runs.
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
        return workspaceRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> findMine(String userId) {
        return memberRepository.findByUserId(userId).stream()
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .map(WorkspaceMember::getWorkspace)
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse findById(String id) {
        return toResponse(getOrThrow(id));
    }

    /** The creator is seated as ADMIN so a new workspace is never orphaned. */
    @Transactional
    public WorkspaceResponse create(WorkspaceRequest request, String currentUserId) {
        User creator = userRepository.findById(currentUserId)
            .orElseThrow(() -> ApiException.notFound("User", currentUserId));

        Workspace workspace = workspaceRepository.save(Workspace.builder()
            .name(request.name())
            .department(request.department())
            .createdBy(creator)
            .build());

        memberRepository.save(WorkspaceMember.builder()
            .workspace(workspace)
            .user(creator)
            .invitedEmail(creator.getEmail())
            .role(User.UserRole.ADMIN)
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
        return memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId)
            .stream()
            .map(MemberResponse::from)
            .toList();
    }

    @Transactional
    public MemberResponse invite(String workspaceId, InviteMemberRequest request) {
        Workspace workspace = getOrThrow(workspaceId);

        memberRepository.findByWorkspaceIdAndInvitedEmail(workspaceId, request.email())
            .ifPresent(existing -> {
                throw ApiException.conflict(request.email() + " has already been invited to this workspace");
            });

        User existingUser = userRepository.findByEmail(request.email()).orElse(null);

        WorkspaceMember member = memberRepository.save(WorkspaceMember.builder()
            .workspace(workspace)
            .user(existingUser)
            .invitedEmail(request.email())
            .role(request.role())
            .status(existingUser == null ? MemberStatus.INVITED : MemberStatus.ACTIVE)
            .build());

        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateMember(String workspaceId, String memberId, UpdateMemberRequest request) {
        WorkspaceMember member = memberOrThrow(workspaceId, memberId);
        if (request.role() != null) {
            member.setRole(request.role());
        }
        if (request.status() != null) {
            member.setStatus(request.status());
        }
        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public void removeMember(String workspaceId, String memberId) {
        memberRepository.delete(memberOrThrow(workspaceId, memberId));
    }

    /** Called after registration so seats invited by email become active. */
    @Transactional
    public void linkPendingInvitations(User user) {
        memberRepository.findAll().stream()
            .filter(member -> member.getUser() == null)
            .filter(member -> member.getInvitedEmail().equalsIgnoreCase(user.getEmail()))
            .forEach(member -> {
                member.setUser(user);
                member.setStatus(MemberStatus.ACTIVE);
                memberRepository.save(member);
            });
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
            projectRepository.countByWorkspaceId(workspace.getId())
        );
    }
}
