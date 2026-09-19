package com.stemotion.domain.script.controller;

import com.stemotion.domain.script.dto.ScriptDtos.CreateScriptRequest;
import com.stemotion.domain.script.dto.ScriptDtos.ReviewDecisionRequest;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptResponse;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptSummaryResponse;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptVersionResponse;
import com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest;
import com.stemotion.domain.script.dto.ScriptDtos.VersionDiffResponse;
import com.stemotion.domain.script.service.ScriptService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Scripts", description = "Script studio and the editorial review workflow")
public class ScriptController {

    private final ScriptService scriptService;

    @GetMapping("/workspaces/{workspaceId}/scripts")
    @Operation(summary = "List the scripts of a workspace")
    public ResponseEntity<List<ScriptSummaryResponse>> findByWorkspace(@PathVariable String workspaceId) {
        return ResponseEntity.ok(scriptService.findByWorkspace(workspaceId));
    }

    @PostMapping("/workspaces/{workspaceId}/scripts")
    @Operation(summary = "Create a clip project and its first script version")
    @PreAuthorize("hasAnyRole('WRITER', 'ADMIN')")
    public ResponseEntity<ScriptResponse> create(
        @PathVariable String workspaceId,
        @Valid @RequestBody CreateScriptRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        ScriptResponse created = scriptService.create(workspaceId, request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/scripts/mine")
    @Operation(summary = "Scripts owned by the authenticated writer")
    public ResponseEntity<List<ScriptSummaryResponse>> findMine(
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(scriptService.findByOwner(principal.getUsername()));
    }

    @GetMapping("/scripts/review-queue")
    @Operation(summary = "Scripts waiting for an editorial decision")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<List<ScriptSummaryResponse>> reviewQueue() {
        return ResponseEntity.ok(scriptService.findReviewQueue());
    }

    @GetMapping("/scripts/{id}")
    @Operation(summary = "Full script with its ordered scenes")
    public ResponseEntity<ScriptResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(scriptService.findById(id));
    }

    @PutMapping("/scripts/{id}")
    @Operation(summary = "Save the working draft, replacing the scene list")
    public ResponseEntity<ScriptResponse> update(
        @PathVariable String id,
        @Valid @RequestBody UpdateScriptRequest request
    ) {
        return ResponseEntity.ok(scriptService.updateDraft(id, request));
    }

    @PostMapping("/scripts/{id}/submit-review")
    @Operation(summary = "Writer submits the draft for review")
    @PreAuthorize("hasAnyRole('WRITER', 'ADMIN')")
    public ResponseEntity<ScriptResponse> submitForReview(
        @PathVariable String id,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(scriptService.submitForReview(id, principal.getUsername()));
    }

    @PostMapping("/scripts/{id}/approve")
    @Operation(summary = "Reviewer approves the script")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<ScriptResponse> approve(
        @PathVariable String id,
        @RequestBody(required = false) ReviewDecisionRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(scriptService.approve(id, request, principal.getUsername()));
    }

    @PostMapping("/scripts/{id}/request-changes")
    @Operation(summary = "Reviewer returns the script and opens a new version")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<ScriptResponse> requestChanges(
        @PathVariable String id,
        @RequestBody(required = false) ReviewDecisionRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(scriptService.requestChanges(id, request, principal.getUsername()));
    }

    @GetMapping("/scripts/{id}/versions")
    @Operation(summary = "Version history of a script")
    public ResponseEntity<List<ScriptVersionResponse>> versions(@PathVariable String id) {
        return ResponseEntity.ok(scriptService.findVersions(id));
    }

    @GetMapping("/scripts/{id}/diff")
    @Operation(summary = "Two versions side by side for the diff view")
    public ResponseEntity<VersionDiffResponse> diff(
        @PathVariable String id,
        @RequestParam String left,
        @RequestParam String right
    ) {
        return ResponseEntity.ok(scriptService.diffVersions(id, left, right));
    }

    @DeleteMapping("/scripts/{id}")
    @PreAuthorize("hasAnyRole('WRITER', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        scriptService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
