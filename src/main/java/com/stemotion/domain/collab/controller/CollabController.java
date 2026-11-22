package com.stemotion.domain.collab.controller;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.collab.dto.CollabDtos.AiSettingsRequest;
import com.stemotion.domain.collab.dto.CollabDtos.AiSettingsResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ContributionsResponse;
import com.stemotion.domain.collab.dto.CollabDtos.DecisionRequest;
import com.stemotion.domain.collab.dto.CollabDtos.JoinRequest;
import com.stemotion.domain.collab.dto.CollabDtos.JoinRequestResponse;
import com.stemotion.domain.collab.dto.CollabDtos.LeaderDashboardResponse;
import com.stemotion.domain.collab.dto.CollabDtos.LeaseResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ProjectTeamResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ProposalResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ProposeRequest;
import com.stemotion.domain.collab.entity.CollabEnums.LeaseResource;
import com.stemotion.domain.collab.service.AiSettingsService;
import com.stemotion.domain.collab.service.ContributionService;
import com.stemotion.domain.collab.service.EditLeaseService;
import com.stemotion.domain.collab.service.LeaderDashboardService;
import com.stemotion.domain.collab.service.ProjectCollabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Proposals, project teams, edit leases, contributions, the Leader dashboard and AI settings (UC-29 to UC-34). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Collaboration", description = "Project proposals, project roles, edit leases, contributions and AI budgets")
public class CollabController {
    private final ProjectCollabService collab;
    private final EditLeaseService leases;
    private final ContributionService contributions;
    private final LeaderDashboardService dashboard;
    private final AiSettingsService aiSettings;

    // ---------------------------------------------------------------- proposals (UC-29)

    @PostMapping("/workspaces/{workspaceId}/proposals")
    @Operation(summary = "Propose a project (Writer)")
    public ResponseEntity<ProposalResponse> propose(@PathVariable String workspaceId, @Valid @RequestBody ProposeRequest request,
                                                    @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collab.propose(workspaceId, request, me.getUsername()));
    }

    @GetMapping("/workspaces/{workspaceId}/proposals")
    @Operation(summary = "Proposals of a workspace")
    public List<ProposalResponse> proposals(@PathVariable String workspaceId, @AuthenticationPrincipal UserDetails me) {
        return collab.proposals(workspaceId, me.getUsername());
    }

    @PostMapping("/proposals/{id}/approve")
    @Operation(summary = "Approve a proposal (Leader); creates the project in DRAFT")
    public ProposalResponse approve(@PathVariable String id, @AuthenticationPrincipal UserDetails me) {
        return collab.approve(id, me.getUsername());
    }

    @PostMapping("/proposals/{id}/reject")
    @Operation(summary = "Reject a proposal with a reason (Leader)")
    public ProposalResponse reject(@PathVariable String id, @Valid @RequestBody DecisionRequest request,
                                   @AuthenticationPrincipal UserDetails me) {
        return collab.reject(id, request.reason(), me.getUsername());
    }

    // ---------------------------------------------------------------- team and join requests (UC-30)

    @GetMapping("/projects/{id}/team")
    @Operation(summary = "Project members and missing roles")
    public ProjectTeamResponse team(@PathVariable String id, @AuthenticationPrincipal UserDetails me) {
        return collab.team(id, me.getUsername());
    }

    @PostMapping("/projects/{id}/join-requests")
    @Operation(summary = "Ask to join a project with a role")
    public ResponseEntity<JoinRequestResponse> requestJoin(@PathVariable String id, @Valid @RequestBody JoinRequest request,
                                                           @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collab.requestJoin(id, request.role(), me.getUsername()));
    }

    @GetMapping("/projects/{id}/join-requests")
    @Operation(summary = "Join requests of a project (Leader: all; others: their own)")
    public List<JoinRequestResponse> joinRequests(@PathVariable String id, @AuthenticationPrincipal UserDetails me) {
        return collab.joinRequests(id, me.getUsername());
    }

    @PostMapping("/join-requests/{id}/approve")
    @Operation(summary = "Approve a join request (Leader)")
    public JoinRequestResponse approveJoin(@PathVariable String id, @AuthenticationPrincipal UserDetails me) {
        return collab.approveJoin(id, me.getUsername());
    }

    @PostMapping("/join-requests/{id}/reject")
    @Operation(summary = "Reject a join request (Leader)")
    public JoinRequestResponse rejectJoin(@PathVariable String id, @Valid @RequestBody(required = false) DecisionRequest request,
                                          @AuthenticationPrincipal UserDetails me) {
        return collab.rejectJoin(id, request == null ? null : request.reason(), me.getUsername());
    }

    @DeleteMapping("/projects/{id}/members/{memberId}")
    @Operation(summary = "Revoke a project member (Leader); the history stays")
    public ProjectTeamResponse revoke(@PathVariable String id, @PathVariable String memberId, @AuthenticationPrincipal UserDetails me) {
        return collab.revoke(id, memberId, me.getUsername());
    }

    // ---------------------------------------------------------------- edit leases (UC-31)

    @GetMapping("/projects/{id}/leases/{type}")
    @Operation(summary = "Who holds the edit lease of the script draft or the composition")
    public LeaseResponse lease(@PathVariable String id, @PathVariable String type, @AuthenticationPrincipal UserDetails me) {
        return leases.status(resource(type), id, me.getUsername());
    }

    @PostMapping("/projects/{id}/leases/{type}")
    @Operation(summary = "Acquire or renew the edit lease (renew every 30 s; it expires 90 s after the last renewal)")
    public LeaseResponse acquire(@PathVariable String id, @PathVariable String type, @AuthenticationPrincipal UserDetails me) {
        return leases.acquire(resource(type), id, me.getUsername());
    }

    @DeleteMapping("/projects/{id}/leases/{type}")
    @Operation(summary = "Release the edit lease; force=true lets a Leader or an Admin release someone else's")
    public LeaseResponse release(@PathVariable String id, @PathVariable String type,
                                 @RequestParam(defaultValue = "false") boolean force, @AuthenticationPrincipal UserDetails me) {
        return leases.release(resource(type), id, me.getUsername(), force);
    }

    // ---------------------------------------------------------------- contributions and dashboard (UC-32, UC-33)

    @GetMapping("/projects/{id}/contributions")
    @Operation(summary = "Contributions of a project, per member and per type")
    public ContributionsResponse contributions(@PathVariable String id, @AuthenticationPrincipal UserDetails me) {
        return contributions.list(id, me.getUsername());
    }

    @GetMapping("/leader/dashboard")
    @Operation(summary = "Leader inbox and progress board")
    public LeaderDashboardResponse dashboard(@AuthenticationPrincipal UserDetails me) {
        return dashboard.dashboard(me.getUsername());
    }

    // ---------------------------------------------------------------- AI/TTS settings (UC-34)

    @GetMapping("/admin/ai-settings")
    @Operation(summary = "AI/TTS providers, voices and budgets (Admin)")
    public AiSettingsResponse aiSettings(@AuthenticationPrincipal UserDetails me) {
        return aiSettings.get(me.getUsername());
    }

    @PutMapping("/admin/ai-settings")
    @Operation(summary = "Save AI/TTS providers, voices and budgets (Admin)")
    public AiSettingsResponse saveAiSettings(@Valid @RequestBody AiSettingsRequest request, @AuthenticationPrincipal UserDetails me) {
        return aiSettings.update(request, me.getUsername());
    }

    private static LeaseResource resource(String type) {
        return switch (type) {
            case "script-draft", "SCRIPT_DRAFT" -> LeaseResource.SCRIPT_DRAFT;
            case "composition", "COMPOSITION" -> LeaseResource.COMPOSITION;
            default -> throw ApiException.badRequest("Lease type must be script-draft or composition");
        };
    }
}
