package com.stemotion.domain.collab.service;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.Membership;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.collab.dto.CollabDtos.BoardCard;
import com.stemotion.domain.collab.dto.CollabDtos.JoinRequestResponse;
import com.stemotion.domain.collab.dto.CollabDtos.LeaderDashboardResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ProposalResponse;
import com.stemotion.domain.collab.entity.CollabEnums.JoinRequestStatus;
import com.stemotion.domain.collab.entity.CollabEnums.ProposalStatus;
import com.stemotion.domain.collab.entity.ProjectJoinRequest;
import com.stemotion.domain.collab.entity.ProjectProposal;
import com.stemotion.domain.collab.repository.ProjectJoinRequestRepository;
import com.stemotion.domain.collab.repository.ProjectProposalRepository;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leader dashboard (UC-33, FR-47): the inbox of pending proposals and join requests of the
 * Leader's workspaces, and the board of their projects by stage with owner and blockers.
 */
@Service
@RequiredArgsConstructor
public class LeaderDashboardService {
    private final WorkspaceAccess access;
    private final WorkspaceRepository workspaces;
    private final ClipProjectRepository projects;
    private final ProjectProposalRepository proposals;
    private final ProjectJoinRequestRepository joins;
    private final FeedbackCommentRepository comments;
    private final RenderJobRepository jobs;
    private final UserRepository users;
    private final ProjectTeamRules team;

    @Transactional(readOnly = true)
    public LeaderDashboardResponse dashboard(String userId) {
        List<String> workspaceIds = access.isAdmin(userId)
            ? workspaces.findAll().stream().map(Workspace::getId).toList()
            : access.memberships(userId).stream().filter(m -> m.active() && m.leader()).map(Membership::workspaceId).distinct().toList();
        if (workspaceIds.isEmpty()) throw ApiException.forbidden("The dashboard is for workspace Leaders");

        List<ClipProject> list = new ArrayList<>();
        for (String id : workspaceIds) list.addAll(projects.findByWorkspaceIdOrderByCreatedAtDesc(id));
        list.removeIf(p -> Boolean.TRUE.equals(p.getArchived()));
        Map<String, ClipProject> byId = list.stream().collect(Collectors.toMap(ClipProject::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<ProjectProposal> pending = proposals.findByWorkspaceIdInAndStatusOrderByCreatedAtAsc(workspaceIds, ProposalStatus.PENDING);
        List<ProjectJoinRequest> waiting = byId.isEmpty() ? List.of()
            : joins.findByProjectIdInAndStatusOrderByCreatedAtAsc(byId.keySet(), JoinRequestStatus.PENDING);
        Map<String, String> names = names(concat(
            pending.stream().map(ProjectProposal::getProposerId).toList(),
            waiting.stream().map(ProjectJoinRequest::getUserId).toList()));

        Map<String, List<BoardCard>> board = new LinkedHashMap<>();
        for (ClipProject p : byId.values()) {
            board.computeIfAbsent(p.getPipelineStatus().name(), k -> new ArrayList<>())
                .add(new BoardCard(p.getId(), p.getTitle(), p.getWorkspace().getId(), p.getPipelineStatus().name(),
                    p.getOwner().getId(), displayName(p.getOwner()), blockers(p)));
        }
        return new LeaderDashboardResponse(
            pending.stream().map(r -> ProposalResponse.of(r, names.get(r.getProposerId()))).toList(),
            waiting.stream().map(r -> JoinRequestResponse.of(r, byId.get(r.getProjectId()).getTitle(), names.get(r.getUserId()))).toList(),
            board);
    }

    /** Open review notes, a missing project role and a failed last render block a project. */
    private List<String> blockers(ClipProject p) {
        List<String> out = new ArrayList<>();
        long open = comments.countByProjectIdAndStatus(p.getId(), FeedbackStatus.OPEN);
        if (open > 0) out.add("OPEN_NOTES:" + open);
        team.missingRoles(p.getId()).forEach(r -> out.add("MISSING_ROLE:" + r));
        jobs.findByProjectIdOrderByCreatedAtDesc(p.getId()).stream().findFirst()
            .filter(j -> j.getStatus() == RenderStatus.FAILED).ifPresent(j -> out.add("RENDER_FAILED"));
        return out;
    }

    private static List<String> concat(Collection<String> a, Collection<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private Map<String, String> names(Collection<String> ids) {
        return users.findAllById(ids.stream().distinct().toList()).stream()
            .collect(Collectors.toMap(User::getId, LeaderDashboardService::displayName));
    }

    private static String displayName(User u) {
        return u.getDisplayName() == null ? u.getFullName() : u.getDisplayName();
    }
}
