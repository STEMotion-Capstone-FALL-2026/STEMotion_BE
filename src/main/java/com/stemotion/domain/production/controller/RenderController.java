package com.stemotion.domain.production.controller;

import com.stemotion.domain.production.dto.ProductionDtos.RenderCallbackRequest;
import com.stemotion.domain.production.dto.ProductionDtos.RenderJobResponse;
import com.stemotion.domain.production.dto.ProductionDtos.StartRenderRequest;
import com.stemotion.domain.production.service.RenderService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/render")
@RequiredArgsConstructor
@Tag(name = "Render", description = "Remotion render jobs dispatched to the Node worker pool")
public class RenderController {

    private final RenderService renderService;

    @PostMapping("/projects/{projectId}/start")
    @Operation(summary = "Queue a render of the approved script")
    @PreAuthorize("@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'PRODUCER')")
    public ResponseEntity<RenderJobResponse> start(
        @PathVariable String projectId,
        @RequestBody(required = false) StartRenderRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        StartRenderRequest body = request == null
            ? new StartRenderRequest(null, null, null)
            : request;
        RenderJobResponse job = renderService.start(projectId, body, principal.getUsername());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/status/{jobId}")
    @Operation(summary = "Poll render progress")
    public ResponseEntity<RenderJobResponse> status(@PathVariable String jobId) {
        return ResponseEntity.ok(renderService.getStatus(jobId));
    }

    @GetMapping("/projects/{projectId}/jobs")
    @Operation(summary = "Render history of a project")
    public ResponseEntity<List<RenderJobResponse>> history(@PathVariable String projectId) {
        return ResponseEntity.ok(renderService.findByProject(projectId));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @PreAuthorize("@workspaceAccess.hasJobRole(#jobId, authentication.name, 'PRODUCER')")
    public ResponseEntity<RenderJobResponse> retry(
        @PathVariable String jobId,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(renderService.retry(jobId, principal.getUsername()));
    }

    @DeleteMapping("/jobs/{jobId}")
    @PreAuthorize("@workspaceAccess.hasJobRole(#jobId, authentication.name, 'PRODUCER')")
    public ResponseEntity<Void> cancel(@PathVariable String jobId) {
        renderService.cancel(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Progress webhook for the render worker. Declared permitAll in SecurityConfig
     * because the worker has no user JWT. Instead, the shared render API key is
     * verified by {@link RenderService#applyCallback}.
     */
    @PostMapping("/callback")
    @Operation(summary = "Progress update pushed by the render worker (secured by X-Render-Api-Key header)")
    public ResponseEntity<RenderJobResponse> callback(
        // Optional at the binding level so a missing key reaches the security
        // check below and comes back as 401, not a generic 400.
        @RequestHeader(value = "X-Render-Api-Key", required = false) String apiKey,
        @Valid @RequestBody RenderCallbackRequest request
    ) {
        return ResponseEntity.ok(renderService.applyCallback(apiKey, request));
    }
}

