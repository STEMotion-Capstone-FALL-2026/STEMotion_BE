package com.stemotion.domain.collab.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.collab.entity.ProjectMember;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role coverage of a project that uses project roles: a Reviewer and a Producer member must be
 * active before the script is submitted (D14); a locked account does not count (A-acct, BR-03).
 */
@Component
@RequiredArgsConstructor
public class ProjectTeamRules {
    static final Set<WorkspaceRole> REQUIRED_BEFORE_SUBMIT = EnumSet.of(WorkspaceRole.REVIEWER, WorkspaceRole.PRODUCER);

    private final WorkspaceAccess access;
    private final UserRepository users;

    /** Roles still missing; always empty for a project that keeps workspace roles. */
    public List<String> missingRoles(String projectId) {
        List<ProjectMember> members = access.projectMembers(projectId);
        if (members.isEmpty()) return List.of();
        Map<String, User> byId = users.findAllById(members.stream().map(ProjectMember::getUserId).toList())
            .stream().collect(Collectors.toMap(User::getId, u -> u));
        Set<WorkspaceRole> covered = members.stream()
            .filter(ProjectMember::active)
            .filter(m -> byId.containsKey(m.getUserId()) && byId.get(m.getUserId()).getStatus() == User.UserStatus.ACTIVE)
            .map(ProjectMember::getRole)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(WorkspaceRole.class)));
        return REQUIRED_BEFORE_SUBMIT.stream().filter(r -> !covered.contains(r)).map(Enum::name).toList();
    }

    public void requireCoverageBeforeSubmit(String projectId) {
        List<String> missing = missingRoles(projectId);
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_ROLES_MISSING",
                "The project needs an active " + String.join(" and ", missing) + " member before the script is submitted",
                Map.of("missingRoles", missing));
        }
    }
}
