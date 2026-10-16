package com.stemotion.core.security;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component("workspaceAccess")
@RequiredArgsConstructor
public class WorkspaceAccess {
    private final UserRepository users;
    private final WorkspaceMemberRepository members;
    private final ClipProjectRepository projects;
    private final RenderJobRepository jobs;
    private final FeedbackCommentRepository comments;

    @Transactional(readOnly = true)
    public boolean isAdmin(String userId) {
        return users.findById(userId).map(u -> u.getRole() == User.UserRole.ADMIN).orElse(false);
    }

    /**
     * Archiving removes a project for the whole workspace, so it is kept to the
     * person responsible for it (the owner), the workspace leader and admins.
     */
    @Transactional(readOnly = true)
    public boolean canArchiveProject(String projectId, String userId) {
        if (isAdmin(userId)) return true;
        return projects.findById(projectId)
            .map(project -> project.getOwner().getId().equals(userId)
                || isLeader(project.getWorkspace().getId(), userId))
            // A missing project is reported as 404 by the service, not as 403 here.
            .orElse(true);
    }

    /** Role check for a QA comment, through the project it belongs to. */
    @Transactional(readOnly = true)
    public boolean hasCommentRole(String commentId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        return comments.findById(commentId)
            .map(comment -> hasProjectRole(comment.getProject().getId(), userId, roleNames))
            .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isLeader(String workspaceId, String userId) {
        if (isAdmin(userId)) return true;
        return members.findByUserId(userId).stream().anyMatch(member ->
            member.getStatus() == MemberStatus.ACTIVE
            && member.getWorkspace().getId().equals(workspaceId)
            && member.isLeader());
    }

    @Transactional(readOnly = true)
    public void requireLeaderOrAdmin(String workspaceId, String userId) {
        if (!isLeader(workspaceId, userId)) {
            throw ApiException.forbidden("Requires workspace leader or system administrator access");
        }
    }

    @Transactional(readOnly = true)
    public boolean canRead(String workspaceId, String userId) {
        return isAdmin(userId) || members.findByUserId(userId).stream().anyMatch(member ->
            member.getStatus() == MemberStatus.ACTIVE && member.getWorkspace().getId().equals(workspaceId));
    }

    @Transactional(readOnly = true)
    public void requireWorkspace(String workspaceId, String userId, boolean manage) {
        if (isAdmin(userId)) return;
        if (manage) {
            requireLeaderOrAdmin(workspaceId, userId);
        } else {
            if (!canRead(workspaceId, userId)) {
                throw ApiException.forbidden("You do not have access to this workspace");
            }
        }
    }

    @Transactional(readOnly = true)
    public void requireProject(String projectId, String userId, boolean editDraft) {
        ClipProject project = projects.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("Script", projectId));
        if (editDraft) {
            requireProjectRole(projectId, userId, Set.of(User.UserRole.WRITER));
        } else {
            requireWorkspace(project.getWorkspace().getId(), userId, false);
        }
    }

    @Transactional(readOnly = true)
    public void requireProjectRole(String projectId, String userId, Set<User.UserRole> allowedRoles) {
        if (isAdmin(userId)) return;
        ClipProject project = projects.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("Script", projectId));
        requireWorkspaceRole(project.getWorkspace().getId(), userId, allowedRoles);
    }

    @Transactional(readOnly = true)
    public void requireWorkspaceRole(String workspaceId, String userId, Set<User.UserRole> allowedRoles) {
        if (isAdmin(userId)) return;
        boolean allowed = members.findByUserId(userId).stream().anyMatch(member ->
            member.getStatus() == MemberStatus.ACTIVE
            && member.getWorkspace().getId().equals(workspaceId)
            && allowedRoles.contains(member.getRole()));
        if (!allowed) {
            throw ApiException.forbidden("You do not have the required role in this workspace");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasWorkspaceRole(String workspaceId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        Set<User.UserRole> roles = Arrays.stream(roleNames)
            .map(User.UserRole::valueOf)
            .collect(Collectors.toSet());
        return members.findByUserId(userId).stream().anyMatch(member ->
            member.getStatus() == MemberStatus.ACTIVE
            && member.getWorkspace().getId().equals(workspaceId)
            && roles.contains(member.getRole()));
    }

    @Transactional(readOnly = true)
    public boolean hasProjectRole(String projectId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        return projects.findById(projectId)
            .map(project -> hasWorkspaceRole(project.getWorkspace().getId(), userId, roleNames))
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasJobRole(String jobId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        return jobs.findById(jobId)
            .map(job -> hasProjectRole(job.getProject().getId(), userId, roleNames))
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isReviewerAnywhere(String userId) {
        if (isAdmin(userId)) return true;
        return members.findByUserId(userId).stream().anyMatch(m ->
            m.getStatus() == MemberStatus.ACTIVE && m.getRole() == User.UserRole.REVIEWER);
    }

    /** Whether the user holds one of these roles, actively, in any workspace. Roles are per workspace. */
    @Transactional(readOnly = true)
    public boolean hasRoleAnywhere(String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        Set<User.UserRole> roles = Arrays.stream(roleNames)
            .map(User.UserRole::valueOf)
            .collect(Collectors.toSet());
        return members.findByUserId(userId).stream().anyMatch(m ->
            m.getStatus() == MemberStatus.ACTIVE && roles.contains(m.getRole()));
    }

    /**
     * AI is used by writers (script assistance) and producers (narration,
     * captions and asset suggestions in production), as the proposal lists.
     */
    @Transactional(readOnly = true)
    public void requireAiAccess(String userId) {
        if (!hasRoleAnywhere(userId, "WRITER", "PRODUCER")) {
            throw ApiException.forbidden("Requires WRITER or PRODUCER membership in a workspace, or ADMIN role");
        }
    }
}
