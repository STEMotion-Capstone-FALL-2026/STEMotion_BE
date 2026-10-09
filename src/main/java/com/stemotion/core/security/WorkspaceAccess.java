package com.stemotion.core.security;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WorkspaceAccess {
    private final UserRepository users;
    private final WorkspaceMemberRepository members;
    private final ClipProjectRepository projects;

    @Transactional(readOnly = true)
    public boolean canRead(String workspaceId, String userId) {
        return isAdmin(userId) || members.findByUserId(userId).stream().anyMatch(member ->
            member.getStatus() == MemberStatus.ACTIVE && member.getWorkspace().getId().equals(workspaceId));
    }

    @Transactional(readOnly = true)
    public void requireWorkspace(String workspaceId, String userId, boolean manage) {
        if (isAdmin(userId)) return;
        boolean allowed = members.findByUserId(userId).stream().anyMatch(member ->
            member.getStatus() == MemberStatus.ACTIVE && member.getWorkspace().getId().equals(workspaceId)
            && (!manage || member.getRole() == User.UserRole.ADMIN));
        if (!allowed) throw ApiException.forbidden("You do not have access to this workspace");
    }

    @Transactional(readOnly = true)
    public void requireProject(String projectId, String userId, boolean editDraft) {
        ClipProject project = projects.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("Script", projectId));
        requireWorkspace(project.getWorkspace().getId(), userId, false);
        if (editDraft && !isAdmin(userId) && !project.getOwner().getId().equals(userId)) {
            requireWorkspace(project.getWorkspace().getId(), userId, true);
        }
    }

    private boolean isAdmin(String userId) {
        return users.findById(userId).map(u -> u.getRole() == User.UserRole.ADMIN).orElse(false);
    }
}
