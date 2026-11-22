package com.stemotion.domain.collab.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.collab.dto.CollabDtos.JoinRequestResponse;
import com.stemotion.domain.collab.dto.CollabDtos.MemberResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ProjectTeamResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ProposalResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ProposeRequest;
import com.stemotion.domain.collab.entity.CollabEnums.JoinRequestStatus;
import com.stemotion.domain.collab.entity.CollabEnums.MemberStatus;
import com.stemotion.domain.collab.entity.CollabEnums.ProposalStatus;
import com.stemotion.domain.collab.entity.ProjectJoinRequest;
import com.stemotion.domain.collab.entity.ProjectMember;
import com.stemotion.domain.collab.entity.ProjectProposal;
import com.stemotion.domain.collab.repository.ProjectJoinRequestRepository;
import com.stemotion.domain.collab.repository.ProjectMemberRepository;
import com.stemotion.domain.collab.repository.ProjectProposalRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.dto.ScriptDtos.CreateScriptRequest;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Project proposals (UC-29), join requests and revocation (UC-30) for projects that use project
 * roles. Decisions D08/D09, D12 to D15: a Writer who is a Leader approves their own proposal; with
 * several Leaders the first decision wins; a revoked member keeps the history and may ask again;
 * an Admin may override any decision, written to the audit log.
 */
@Service
@RequiredArgsConstructor
public class ProjectCollabService {
    private static final Logger AUDIT = LoggerFactory.getLogger("stemotion.audit");

    private final ProjectProposalRepository proposals;
    private final ProjectMemberRepository members;
    private final ProjectJoinRequestRepository joins;
    private final ClipProjectRepository projects;
    private final WorkspaceAccess access;
    private final ScriptService scripts;
    private final UserRepository users;
    private final ProjectTeamRules team;

    // ------------------------------------------------------------------ proposals (UC-29)

    @Transactional
    public ProposalResponse propose(String workspaceId, ProposeRequest request, String userId) {
        access.requireWorkspaceRole(workspaceId, userId, Set.of(WorkspaceRole.WRITER));
        ProjectProposal saved = proposals.save(ProjectProposal.builder()
            .workspaceId(workspaceId).proposerId(userId).title(request.title().trim())
            .subject(request.subject()).gradeLevel(blankToNull(request.gradeLevel())).build());
        return ProposalResponse.of(saved, names(List.of(userId)).get(userId));
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> proposals(String workspaceId, String userId) {
        access.requireWorkspace(workspaceId, userId, false);
        List<ProjectProposal> all = proposals.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        Map<String, String> names = names(all.stream().map(ProjectProposal::getProposerId).toList());
        return all.stream().map(p -> ProposalResponse.of(p, names.get(p.getProposerId()))).toList();
    }

    @Transactional
    public ProposalResponse approve(String proposalId, String userId) {
        ProjectProposal proposal = proposals.lockById(proposalId).orElseThrow(() -> ApiException.notFound("Proposal", proposalId));
        access.requireLeaderOrAdmin(proposal.getWorkspaceId(), userId);
        requirePending(proposal);
        var created = scripts.create(proposal.getWorkspaceId(),
            new CreateScriptRequest(proposal.getTitle(), proposal.getSubject(), proposal.getGradeLevel(), null, List.of()),
            proposal.getProposerId());
        members.save(ProjectMember.builder().projectId(created.id()).userId(proposal.getProposerId())
            .role(WorkspaceRole.WRITER).build());
        proposal.setStatus(ProposalStatus.APPROVED);
        proposal.setDecidedBy(userId);
        proposal.setDecidedAt(LocalDateTime.now());
        proposal.setProjectId(created.id());
        auditOverride("proposal-approve", proposal.getWorkspaceId(), proposalId, userId);
        return ProposalResponse.of(proposals.save(proposal), names(List.of(proposal.getProposerId())).get(proposal.getProposerId()));
    }

    @Transactional
    public ProposalResponse reject(String proposalId, String reason, String userId) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("A rejection needs a reason");
        ProjectProposal proposal = proposals.lockById(proposalId).orElseThrow(() -> ApiException.notFound("Proposal", proposalId));
        access.requireLeaderOrAdmin(proposal.getWorkspaceId(), userId);
        requirePending(proposal);
        proposal.setStatus(ProposalStatus.REJECTED);
        proposal.setDecisionReason(reason.trim());
        proposal.setDecidedBy(userId);
        proposal.setDecidedAt(LocalDateTime.now());
        auditOverride("proposal-reject", proposal.getWorkspaceId(), proposalId, userId);
        return ProposalResponse.of(proposals.save(proposal), names(List.of(proposal.getProposerId())).get(proposal.getProposerId()));
    }

    // ------------------------------------------------------------------ team and join requests (UC-30)

    @Transactional(readOnly = true)
    public ProjectTeamResponse team(String projectId, String userId) {
        access.requireProject(projectId, userId, false);
        List<ProjectMember> list = members.findByProjectIdOrderByCreatedAtAsc(projectId);
        Map<String, String> names = names(list.stream().map(ProjectMember::getUserId).toList());
        return new ProjectTeamResponse(projectId, !list.isEmpty(),
            list.stream().map(m -> MemberResponse.of(m, names.get(m.getUserId()))).toList(), team.missingRoles(projectId));
    }

    @Transactional
    public JoinRequestResponse requestJoin(String projectId, WorkspaceRole role, String userId) {
        ClipProject project = project(projectId);
        String workspaceId = project.getWorkspace().getId();
        if (!access.canRead(workspaceId, userId)) throw ApiException.forbidden("You are not a member of this workspace");
        if (!access.usesProjectRoles(projectId)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_USES_WORKSPACE_ROLES",
                "This project was created before project roles; its team follows the workspace roles");
        }
        if (access.projectRole(projectId, userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PROJECT_MEMBER", "You are already a member of this project");
        }
        if (joins.existsByProjectIdAndUserIdAndStatus(projectId, userId, JoinRequestStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, "JOIN_REQUEST_PENDING", "Your request to join is already waiting for a Leader");
        }
        ProjectJoinRequest saved = joins.save(ProjectJoinRequest.builder().projectId(projectId).userId(userId).role(role).build());
        return JoinRequestResponse.of(saved, project.getTitle(), names(List.of(userId)).get(userId));
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> joinRequests(String projectId, String userId) {
        ClipProject project = project(projectId);
        boolean leader = access.isLeader(project.getWorkspace().getId(), userId);
        if (!leader) access.requireWorkspace(project.getWorkspace().getId(), userId, false);
        List<ProjectJoinRequest> list = joins.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(r -> leader || r.getUserId().equals(userId)).toList();
        Map<String, String> names = names(list.stream().map(ProjectJoinRequest::getUserId).toList());
        return list.stream().map(r -> JoinRequestResponse.of(r, project.getTitle(), names.get(r.getUserId()))).toList();
    }

    @Transactional
    public JoinRequestResponse approveJoin(String requestId, String userId) {
        ProjectJoinRequest request = joins.lockById(requestId).orElseThrow(() -> ApiException.notFound("JoinRequest", requestId));
        ClipProject project = project(request.getProjectId());
        access.requireLeaderOrAdmin(project.getWorkspace().getId(), userId);
        requirePending(request);
        ProjectMember member = members.findByProjectIdAndUserId(request.getProjectId(), request.getUserId())
            .orElseGet(() -> ProjectMember.builder().projectId(request.getProjectId()).userId(request.getUserId()).build());
        member.setRole(request.getRole());
        member.setStatus(MemberStatus.ACTIVE);
        member.setRevokedBy(null);
        member.setRevokedAt(null);
        members.save(member);
        request.setStatus(JoinRequestStatus.APPROVED);
        request.setDecidedBy(userId);
        request.setDecidedAt(LocalDateTime.now());
        auditOverride("join-approve", project.getWorkspace().getId(), requestId, userId);
        return JoinRequestResponse.of(joins.save(request), project.getTitle(), names(List.of(request.getUserId())).get(request.getUserId()));
    }

    @Transactional
    public JoinRequestResponse rejectJoin(String requestId, String reason, String userId) {
        ProjectJoinRequest request = joins.lockById(requestId).orElseThrow(() -> ApiException.notFound("JoinRequest", requestId));
        ClipProject project = project(request.getProjectId());
        access.requireLeaderOrAdmin(project.getWorkspace().getId(), userId);
        requirePending(request);
        request.setStatus(JoinRequestStatus.REJECTED);
        request.setReason(blankToNull(reason));
        request.setDecidedBy(userId);
        request.setDecidedAt(LocalDateTime.now());
        auditOverride("join-reject", project.getWorkspace().getId(), requestId, userId);
        return JoinRequestResponse.of(joins.save(request), project.getTitle(), names(List.of(request.getUserId())).get(request.getUserId()));
    }

    /** D15: the member keeps the history and may ask to join again. */
    @Transactional
    public ProjectTeamResponse revoke(String projectId, String memberUserId, String userId) {
        ClipProject project = project(projectId);
        access.requireLeaderOrAdmin(project.getWorkspace().getId(), userId);
        ProjectMember member = members.findByProjectIdAndUserId(projectId, memberUserId)
            .filter(ProjectMember::active)
            .orElseThrow(() -> ApiException.notFound("ProjectMember", memberUserId));
        member.setStatus(MemberStatus.REVOKED);
        member.setRevokedBy(userId);
        member.setRevokedAt(LocalDateTime.now());
        members.save(member);
        auditOverride("member-revoke", project.getWorkspace().getId(), projectId + "/" + memberUserId, userId);
        return team(projectId, userId);
    }

    // ------------------------------------------------------------------ helpers

    private ClipProject project(String projectId) {
        return projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Script", projectId));
    }

    private static void requirePending(ProjectProposal p) {
        if (p.getStatus() != ProposalStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "PROPOSAL_ALREADY_DECIDED",
                "This proposal was already " + p.getStatus().name().toLowerCase() + "; reload the list");
        }
    }

    private static void requirePending(ProjectJoinRequest r) {
        if (r.getStatus() != JoinRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "JOIN_REQUEST_ALREADY_DECIDED",
                "This request was already " + r.getStatus().name().toLowerCase() + "; reload the list");
        }
    }

    /** An Admin acting without a leader seat in the workspace is an override (D15). */
    private void auditOverride(String action, String workspaceId, String target, String userId) {
        boolean leaderSeat = access.memberships(userId).stream()
            .anyMatch(m -> m.active() && m.leader() && m.workspaceId().equals(workspaceId));
        if (!leaderSeat && access.isAdmin(userId)) {
            AUDIT.info("admin-override action={} workspace={} target={} by={}", action, workspaceId, target, userId);
        }
    }

    private Map<String, String> names(Collection<String> ids) {
        return users.findAllById(ids.stream().distinct().toList()).stream()
            .collect(Collectors.toMap(User::getId, u -> u.getDisplayName() == null ? u.getFullName() : u.getDisplayName()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
