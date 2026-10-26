package com.stemotion.domain.workspace.controller;

import com.stemotion.domain.workspace.dto.WorkspaceDtos.AddMemberRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.CandidateUserResponse;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.MemberResponse;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.UpdateMemberRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.WorkspaceRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.WorkspaceResponse;
import com.stemotion.domain.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspaces", description = "Subject departments and their members")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    @Operation(summary = "Workspaces the user can open: all of them for an administrator, memberships otherwise")
    public ResponseEntity<List<WorkspaceResponse>> findAll(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(workspaceService.findVisible(principal.getUsername()));
    }

    @GetMapping("/mine")
    @Operation(summary = "Workspaces the authenticated user belongs to")
    public ResponseEntity<List<WorkspaceResponse>> findMine(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(workspaceService.findMine(principal.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(workspaceService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin creates a workspace with a leader and at least 3 members covering WRITER, REVIEWER and PRODUCER")
    public ResponseEntity<WorkspaceResponse> create(
        @Valid @RequestBody WorkspaceRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        WorkspaceResponse created = workspaceService.create(request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkspaceResponse> update(
        @PathVariable String id,
        @Valid @RequestBody WorkspaceRequest request
    ) {
        return ResponseEntity.ok(workspaceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        workspaceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<MemberResponse>> members(@PathVariable String id) {
        return ResponseEntity.ok(workspaceService.findMembers(id));
    }

    @GetMapping("/{id}/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List active users eligible to be added to this workspace")
    public ResponseEntity<List<CandidateUserResponse>> candidates(@PathVariable String id) {
        return ResponseEntity.ok(workspaceService.findCandidates(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Administrator assigns an existing user to this workspace")
    public ResponseEntity<MemberResponse> addMember(
        @PathVariable String id,
        @Valid @RequestBody AddMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.addMember(id, request));
    }

    @PatchMapping("/{id}/members/{memberId}")
    @Operation(summary = "Leader or administrator changes a member's role; status and leader designation are administrator-only")
    public ResponseEntity<MemberResponse> updateMember(
        @PathVariable String id,
        @PathVariable String memberId,
        @RequestBody UpdateMemberRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(workspaceService.updateMember(id, memberId, request, principal.getUsername()));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Administrator removes a member from this workspace")
    public ResponseEntity<Void> removeMember(@PathVariable String id, @PathVariable String memberId) {
        workspaceService.removeMember(id, memberId);
        return ResponseEntity.noContent().build();
    }
}
