package com.stemotion.domain.ai.controller;

import com.stemotion.domain.ai.service.VideoStoryboardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scripts/{projectId}/ai")
@RequiredArgsConstructor
public class VideoStoryboardController {
    private final VideoStoryboardService storyboards;
    public record StoryboardRequest(@NotNull @Min(1) Integer baseVersion, @NotNull @Min(0) Integer baseRevision) {}

    @PostMapping("/storyboard")
    @PreAuthorize("@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'WRITER', 'PRODUCER')")
    public VideoStoryboardService.Suggestion suggest(@PathVariable String projectId,
            @Valid @RequestBody StoryboardRequest request) {
        return storyboards.suggest(projectId, request.baseVersion(), request.baseRevision());
    }
}
