package com.stemotion.domain.production.controller;

import com.stemotion.domain.production.dto.ProductionDtos.SyncDurationsRequest;
import com.stemotion.domain.production.dto.ProductionDtos.SyncDurationsResponse;
import com.stemotion.domain.production.service.NarrationToolsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/scripts/{projectId}")
@RequiredArgsConstructor
@Tag(name = "Narration", description = "TTS preview and audio-timed scene durations through the API")
public class NarrationToolsController {

    private final NarrationToolsService narrationToolsService;

    /** Writers and producers listen to narration; the access interceptor checks membership. */
    @GetMapping(value = "/tts-preview", produces = "audio/mpeg")
    @Operation(summary = "Narration audio of a text or of a scene (sceneId), synthesised like a render")
    @PreAuthorize("@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'WRITER', 'PRODUCER', 'REVIEWER')")
    public ResponseEntity<byte[]> ttsPreview(
        @PathVariable String projectId,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) String sceneId,
        @RequestParam(required = false) String voice
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
            .body(narrationToolsService.ttsPreview(projectId, text, sceneId));
    }

    @PostMapping("/sync-durations")
    @Operation(summary = "Time every scene from its TTS audio (and Whisper word timings) and save it")
    @PreAuthorize("@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'WRITER', 'PRODUCER')")
    public ResponseEntity<SyncDurationsResponse> syncDurations(
        @PathVariable String projectId,
        @RequestBody(required = false) SyncDurationsRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(narrationToolsService.syncDurations(projectId,
            request == null ? null : request.baseRevision(), principal.getUsername()));
    }
}
