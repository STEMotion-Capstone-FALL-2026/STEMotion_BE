package com.stemotion.domain.youtube.controller;

import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.youtube.dto.YoutubeDtos.ConnectResponse;
import com.stemotion.domain.youtube.dto.YoutubeDtos.ConnectionStatusResponse;
import com.stemotion.domain.youtube.service.YoutubeOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@Tag(name = "YouTube Integration", description = "OAuth channel connection and video publishing")
public class YoutubeOAuthController {

    private final YoutubeOAuthService oauthService;
    private final WorkspaceAccess workspaceAccess;

    @PostMapping("/api/v1/workspaces/{workspaceId}/youtube/connect")
    @Operation(summary = "Initiate YouTube OAuth channel connection for workspace")
    @PreAuthorize("@workspaceAccess.hasWorkspaceRole(#workspaceId, authentication.name, 'PRODUCER') or @workspaceAccess.isAdmin(authentication.name)")
    public ResponseEntity<ConnectResponse> connect(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        workspaceAccess.requireWorkspace(workspaceId, principal.getUsername(), false);
        ConnectResponse response = oauthService.buildAuthorizationUrl(workspaceId, principal.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/youtube/oauth/callback")
    @Operation(summary = "Handle OAuth callback from Google")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state
    ) {
        String targetUrl = oauthService.handleCallback(code, state);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(targetUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/youtube")
    @Operation(summary = "Get YouTube channel connection status for workspace")
    public ResponseEntity<ConnectionStatusResponse> status(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        workspaceAccess.requireWorkspace(workspaceId, principal.getUsername(), false);
        return ResponseEntity.ok(oauthService.getConnectionStatus(workspaceId));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/youtube")
    @Operation(summary = "Revoke YouTube channel connection for workspace")
    @PreAuthorize("@workspaceAccess.hasWorkspaceRole(#workspaceId, authentication.name, 'PRODUCER') or @workspaceAccess.isAdmin(authentication.name)")
    public ResponseEntity<Void> disconnect(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        workspaceAccess.requireWorkspace(workspaceId, principal.getUsername(), false);
        oauthService.revokeConnection(workspaceId, principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
