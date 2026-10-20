package com.stemotion.core.security;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.support.RequestCache;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Workspace and project access rules. A request asks these questions several times (JWT filter,
 * access interceptor, {@code @PreAuthorize}, services), so the user, their memberships and the
 * project ids are each read once per request through {@link RequestCache}; the rules themselves
 * are unchanged.
 */
@Component("workspaceAccess")
@RequiredArgsConstructor
public class WorkspaceAccess {
    private final UserRepository users;
    private final WorkspaceMemberRepository members;
    private final ClipProjectRepository projects;
    private final RenderJobRepository jobs;
    private final FeedbackCommentRepository comments;

    /** The user by id, read once per request (shared with the JWT filter and the interceptor). */
    public Optional<User> user(String userId) {
        return RequestCache.get("user:" + userId, () -> users.findById(userId));
    }

    /** All of the user's seats, read once per request. */
    public List<Membership> memberships(String userId) {
        return RequestCache.get("memberships:" + userId, () -> members.findMembershipsByUserId(userId));
    }

    private Optional<ProjectRef> project(String projectId) {
        return RequestCache.get("project:" + projectId, () -> projects.findRefById(projectId));
    }

    private Optional<Membership> activeSeat(String workspaceId, String userId) {
        return memberships(userId).stream()
            .filter(m -> m.active() && m.workspaceId().equals(workspaceId))
            .findFirst();
    }

    private static Set<User.UserRole> roles(String... roleNames) {
        return Arrays.stream(roleNames).map(User.UserRole::valueOf).collect(Collectors.toSet());
    }

    public boolean isAdmin(String userId) {
        return user(userId).map(u -> u.getRole() == User.UserRole.ADMIN).orElse(false);
    }

    /**
     * Archiving removes a project for the whole workspace, so it is kept to the
     * person responsible for it (the owner), the workspace leader and admins.
     */
    public boolean canArchiveProject(String projectId, String userId) {
        if (isAdmin(userId)) return true;
        return project(projectId)
            .map(ref -> ref.ownerId().equals(userId) || isLeader(ref.workspaceId(), userId))
            // A missing project is reported as 404 by the service, not as 403 here.
            .orElse(true);
    }

    /** Role check for a QA comment, through the project it belongs to. */
    public boolean hasCommentRole(String commentId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        return comments.findProjectIdById(commentId)
            .map(projectId -> hasProjectRole(projectId, userId, roleNames))
            .orElse(true);
    }

    public boolean isLeader(String workspaceId, String userId) {
        if (isAdmin(userId)) return true;
        return activeSeat(workspaceId, userId).map(Membership::leader).orElse(false);
    }

    public void requireLeaderOrAdmin(String workspaceId, String userId) {
        if (!isLeader(workspaceId, userId)) {
            throw ApiException.forbidden("Requires workspace leader or system administrator access");
        }
    }

    public boolean canRead(String workspaceId, String userId) {
        return isAdmin(userId) || activeSeat(workspaceId, userId).isPresent();
    }

    public void requireWorkspace(String workspaceId, String userId, boolean manage) {
        if (isAdmin(userId)) return;
        if (manage) {
            requireLeaderOrAdmin(workspaceId, userId);
        } else if (!canRead(workspaceId, userId)) {
            throw ApiException.forbidden("You do not have access to this workspace");
        }
    }

    public void requireProject(String projectId, String userId, boolean editDraft) {
        ProjectRef ref = project(projectId).orElseThrow(() -> ApiException.notFound("Script", projectId));
        if (editDraft) {
            requireProjectRole(projectId, userId, Set.of(User.UserRole.WRITER));
        } else {
            requireWorkspace(ref.workspaceId(), userId, false);
        }
    }

    public void requireProjectRole(String projectId, String userId, Set<User.UserRole> allowedRoles) {
        if (isAdmin(userId)) return;
        ProjectRef ref = project(projectId).orElseThrow(() -> ApiException.notFound("Script", projectId));
        requireWorkspaceRole(ref.workspaceId(), userId, allowedRoles);
    }

    public void requireWorkspaceRole(String workspaceId, String userId, Set<User.UserRole> allowedRoles) {
        if (isAdmin(userId)) return;
        boolean allowed = activeSeat(workspaceId, userId).map(m -> allowedRoles.contains(m.role())).orElse(false);
        if (!allowed) {
            throw ApiException.forbidden("You do not have the required role in this workspace");
        }
    }

    public boolean hasWorkspaceRole(String workspaceId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        Set<User.UserRole> allowed = roles(roleNames);
        return activeSeat(workspaceId, userId).map(m -> allowed.contains(m.role())).orElse(false);
    }

    public boolean hasProjectRole(String projectId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        return project(projectId)
            .map(ref -> hasWorkspaceRole(ref.workspaceId(), userId, roleNames))
            .orElse(false);
    }

    public boolean hasJobRole(String jobId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        return jobs.findProjectIdById(jobId)
            .map(projectId -> hasProjectRole(projectId, userId, roleNames))
            .orElse(false);
    }

    public boolean isReviewerAnywhere(String userId) {
        return hasRoleAnywhere(userId, "REVIEWER");
    }

    /** Whether the user holds one of these roles, actively, in any workspace. Roles are per workspace. */
    public boolean hasRoleAnywhere(String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        Set<User.UserRole> allowed = roles(roleNames);
        return memberships(userId).stream().anyMatch(m -> m.active() && allowed.contains(m.role()));
    }

    /**
     * AI is used by writers (script assistance) and producers (narration,
     * captions and asset suggestions in production), as the proposal lists.
     */
    public void requireAiAccess(String userId) {
        if (!hasRoleAnywhere(userId, "WRITER", "PRODUCER")) {
            throw ApiException.forbidden("Requires WRITER or PRODUCER membership in a workspace, or ADMIN role");
        }
    }
}
