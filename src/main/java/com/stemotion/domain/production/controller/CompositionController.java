package com.stemotion.domain.production.controller;

import com.stemotion.domain.production.dto.ProductionDtos.CompositionResponse;
import com.stemotion.domain.production.dto.ProductionDtos.UpdateCompositionRequest;
import com.stemotion.domain.production.service.CompositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scripts/{projectId}/composition")
@RequiredArgsConstructor
@Tag(name = "Production", description = "Producer's draft of an approved script")
public class CompositionController {

    private final CompositionService compositionService;

    /** Any member of the workspace may look; the access interceptor checks membership. */
    @GetMapping
    @Operation(summary = "Open the approved script as a production draft (scenes mapped to templates)")
    public ResponseEntity<CompositionResponse> get(@PathVariable String projectId) {
        return ResponseEntity.ok(compositionService.get(projectId));
    }

    @PutMapping
    @Operation(summary = "Save the producer's per-scene edits as the next composition version")
    @PreAuthorize("@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'PRODUCER')")
    public ResponseEntity<CompositionResponse> update(
        @PathVariable String projectId,
        @Valid @RequestBody UpdateCompositionRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(compositionService.update(projectId, request, principal.getUsername()));
    }
}
