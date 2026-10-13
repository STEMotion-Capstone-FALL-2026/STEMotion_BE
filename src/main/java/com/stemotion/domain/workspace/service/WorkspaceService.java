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
import java.time.Instant;
import java.time.Duration;
import java.util.Locale;
import java.util.Base64;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.InvitationResponse;

/**
 * Subject departments and their membership. Inviting a teacher who has no
 * account yet records an inactive seat. Acceptance requires a one-time token
 * delivered privately by the administrator, plus a matching signed-in account.
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
    public InvitationResponse invite(String workspaceId, InviteMemberRequest request) {
        Workspace workspace = getOrThrow(workspaceId);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        WorkspaceMember member = memberRepository
            .findByWorkspaceIdAndInvitedEmailIgnoreCase(workspaceId, email)
            .orElseGet(() -> WorkspaceMember.builder().workspace(workspace).invitedEmail(email).build());
        if (member.getStatus() == MemberStatus.ACTIVE) {
            throw ApiException.conflict("This account is already an active member");
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        member.setUser(null);
        member.setRole(request.role());
        member.setStatus(MemberStatus.INVITED);
        member.setInvitationTokenHash(hashToken(token));
        member.setInvitationExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        member = memberRepository.save(member);
        return new InvitationResponse(MemberResponse.from(member), token, member.getInvitationExpiresAt());
    }

    @Transactional
    public MemberResponse acceptInvitation(String token, String userId) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw ApiException.badRequest("Invalid or expired invitation");
        }
        WorkspaceMember member = memberRepository.findByInvitationTokenHash(hashToken(token))
            .orElseThrow(() -> ApiException.badRequest("Invalid or expired invitation"));
        if (member.getStatus() != MemberStatus.INVITED || member.getInvitationExpiresAt() == null
            || !member.getInvitationExpiresAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("Invalid or expired invitation");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("User", userId));
        if (user.getStatus() != User.UserStatus.ACTIVE
            || !member.getInvitedEmail().equalsIgnoreCase(user.getEmail())) {
            throw ApiException.forbidden("Sign in with the account this invitation was issued to");
        }
        member.setUser(user);
        member.setStatus(MemberStatus.ACTIVE);
        member.setInvitationTokenHash(null);
        member.setInvitationExpiresAt(null);
        return MemberResponse.from(memberRepository.save(member));
    }

    private static String hashToken(String token) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Transactional
    public MemberResponse updateMember(String workspaceId, String memberId, UpdateMemberRequest request) {
        WorkspaceMember member = memberOrThrow(workspaceId, memberId);
        if (request.status() == MemberStatus.ACTIVE && member.getStatus() != MemberStatus.ACTIVE) {
            throw ApiException.badRequest("An inactive seat must accept a new invitation before activation");
        }
        if (request.role() != null) {
            member.setRole(request.role());
        }
        if (request.status() != null) {
            member.setStatus(request.status());
            member.setInvitationTokenHash(null);
            member.setInvitationExpiresAt(null);
        }
        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public void removeMember(String workspaceId, String memberId) {
        memberRepository.delete(memberOrThrow(workspaceId, memberId));
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
