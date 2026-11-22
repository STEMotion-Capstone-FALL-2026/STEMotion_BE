package com.stemotion.core.security;

import com.stemotion.domain.auth.entity.SystemRole;

import com.stemotion.domain.workspace.entity.WorkspaceRole;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.support.RequestCache;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.collab.entity.ProjectMember;
import com.stemotion.domain.collab.repository.ProjectMemberRepository;
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
    private final ProjectMemberRepository projectMembers;

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

    /**
     * Members of a project created from a proposal (V28). A project without members (every project
     * created before V28) keeps the workspace roles, so existing projects and tests keep working.
     */
    public List<ProjectMember> projectMembers(String projectId) {
        return RequestCache.get("project-members:" + projectId, () -> projectMembers.findByProjectIdOrderByCreatedAtAsc(projectId));
    }

    public boolean usesProjectRoles(String projectId) {
        return !projectMembers(projectId).isEmpty();
    }

    /** The user's active project role, when the project uses project roles. */
    public Optional<WorkspaceRole> projectRole(String projectId, String userId) {
        return projectMembers(projectId).stream()
            .filter(m -> m.active() && m.getUserId().equals(userId))
            .map(ProjectMember::getRole).findFirst();
    }

    /** The role a user acts with in a project right now: project role, else workspace role. */
    public Optional<WorkspaceRole> effectiveRole(String projectId, String userId) {
        if (usesProjectRoles(projectId)) return projectRole(projectId, userId);
        return project(projectId).flatMap(ref -> activeSeat(ref.workspaceId(), userId)).map(Membership::role);
    }

    public String workspaceOf(String projectId) {
        return project(projectId).map(ProjectRef::workspaceId).orElseThrow(() -> ApiException.notFound("Script", projectId));
    }

    private Optional<Membership> activeSeat(String workspaceId, String userId) {
        return memberships(userId).stream()
            .filter(m -> m.active() && m.workspaceId().equals(workspaceId))
            .findFirst();
    }

    private static Set<WorkspaceRole> roles(String... roleNames) {
        return Arrays.stream(roleNames).map(WorkspaceRole::valueOf).collect(Collectors.toSet());
    }

    /** A real active seat is required for STEM; deliberately no system-admin bypass. */
    public void requireActiveStemSeat(String workspaceId, String userId, WorkspaceRole role) {
        if(users.findById(userId).filter(u->u.getStatus()==User.UserStatus.ACTIVE).isEmpty())
            throw ApiException.forbidden("Active user required for STEM");
        boolean allowed=members.findMembershipsByUserId(userId).stream()
            .anyMatch(m -> m.active() && m.workspaceId().equals(workspaceId) && m.role()==role);
        if(!allowed)throw ApiException.forbidden("Active "+role+" seat required for STEM");
    }

    /**
     * STEM seat inside a project: a project that uses project roles (V28) asks for that role in the
     * project and any active seat in its workspace; other projects keep the workspace seat rule.
     * Still no system-admin bypass (D-ADM, AT24).
     */
    public void requireActiveStemSeat(String workspaceId, String userId, WorkspaceRole role, String projectId) {
        if (projectId == null || !usesProjectRoles(projectId)) { requireActiveStemSeat(workspaceId, userId, role); return; }
        if(users.findById(userId).filter(u->u.getStatus()==User.UserStatus.ACTIVE).isEmpty())
            throw ApiException.forbidden("Active user required for STEM");
        boolean seat = members.findMembershipsByUserId(userId).stream().anyMatch(m -> m.active() && m.workspaceId().equals(workspaceId));
        if (!seat || projectRole(projectId, userId).filter(r -> r == role).isEmpty())
            throw ApiException.forbidden("Active "+role+" role in this project required for STEM");
    }

    /** D-ADM: an Admin approves a script or a video only as a Reviewer of the project, never by bypass. */
    public void requireReviewerNotAdminBypass(String projectId, String userId) {
        if (isAdmin(userId) && effectiveRole(projectId, userId).filter(r -> r == WorkspaceRole.REVIEWER).isEmpty()) {
            throw ApiException.forbidden("An Admin approves only as a Reviewer of this project (D-ADM)");
        }
    }

    public boolean isAdmin(String userId) {
        return user(userId).map(u -> u.getSystemRole() == SystemRole.ADMIN).orElse(false);
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
            requireProjectRole(projectId, userId, Set.of(WorkspaceRole.WRITER));
        } else {
            requireWorkspace(ref.workspaceId(), userId, false);
            // D13: outside members of a project-role project see its title in lists, not its content.
            // Revoked members keep read access to the history they took part in (D15).
            if (usesProjectRoles(projectId) && !isLeader(ref.workspaceId(), userId)
                && projectMembers(projectId).stream().noneMatch(m -> m.getUserId().equals(userId))) {
                throw ApiException.forbidden("Only members of this project can open it; ask to join the project");
            }
        }
    }

    public void requireProjectRole(String projectId, String userId, Set<WorkspaceRole> allowedRoles) {
        if (isAdmin(userId)) return;
        ProjectRef ref = project(projectId).orElseThrow(() -> ApiException.notFound("Script", projectId));
        if (usesProjectRoles(projectId)) {
            if (projectRole(projectId, userId).filter(allowedRoles::contains).isEmpty()) {
                throw ApiException.forbidden("You do not have the required role in this project");
            }
            return;
        }
        requireWorkspaceRole(ref.workspaceId(), userId, allowedRoles);
    }

    public void requireWorkspaceRole(String workspaceId, String userId, Set<WorkspaceRole> allowedRoles) {
        if (isAdmin(userId)) return;
        boolean allowed = activeSeat(workspaceId, userId).map(m -> allowedRoles.contains(m.role())).orElse(false);
        if (!allowed) {
            throw ApiException.forbidden("You do not have the required role in this workspace");
        }
    }

    public boolean hasWorkspaceRole(String workspaceId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        Set<WorkspaceRole> allowed = roles(roleNames);
        return activeSeat(workspaceId, userId).map(m -> allowed.contains(m.role())).orElse(false);
    }

    public boolean hasProjectRole(String projectId, String userId, String... roleNames) {
        if (isAdmin(userId)) return true;
        if (project(projectId).isEmpty()) return false;
        if (usesProjectRoles(projectId)) {
            Set<WorkspaceRole> allowed = roles(roleNames);
            return projectRole(projectId, userId).filter(allowed::contains).isPresent();
        }
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
        Set<WorkspaceRole> allowed = roles(roleNames);
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
