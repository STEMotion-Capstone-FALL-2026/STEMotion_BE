package com.stemotion.domain.review.controller;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.domain.review.dto.FeedbackDtos.FeedbackRequest;
import com.stemotion.domain.review.dto.FeedbackDtos.FeedbackResponse;
import com.stemotion.domain.review.dto.QaDtos.QaReport;
import com.stemotion.domain.review.service.ClipQaService;
import com.stemotion.domain.review.service.FeedbackService;
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
@Tag(name = "Clip QA", description = "Timestamped review notes and video sign-off")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final ClipQaService clipQaService;
    private final com.stemotion.domain.review.service.ReviewHistoryService reviewHistoryService;

    @GetMapping("/scripts/{projectId}/qa-comments")
    @Operation(summary = "Review notes (with their replies); clipId keeps one render's notes plus unscoped ones")
    public ResponseEntity<List<FeedbackResponse>> findByProject(
        @PathVariable String projectId,
        @RequestParam(required = false) FeedbackStatus status,
        @RequestParam(required = false) String clipId,
        @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
        @org.springframework.web.bind.annotation.RequestParam(required = false) Integer size
    ) {
        return com.stemotion.core.web.PagedResponse.list(com.stemotion.core.web.PageParams.of(page, size),
            () -> feedbackService.findByProject(projectId, status, clipId),
            p -> feedbackService.findByProject(projectId, status, clipId, p));
    }

    @GetMapping("/scripts/{projectId}/review-history")
    @Operation(summary = "Reviewer decisions on scripts, clips and composition revisions, newest first")
    public ResponseEntity<List<com.stemotion.domain.review.dto.FeedbackDtos.ReviewHistoryEntry>> reviewHistory(
            @PathVariable String projectId,
        @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
        @org.springframework.web.bind.annotation.RequestParam(required = false) Integer size) {
        return com.stemotion.core.web.PagedResponse.list(com.stemotion.core.web.PageParams.of(page, size),
            () -> reviewHistoryService.findByProject(projectId), p -> reviewHistoryService.findByProject(projectId, p));
    }

    @PostMapping("/scripts/{projectId}/submit-video-review")
    @Operation(summary = "Send the composition to the reviewer for review in the web player, without rendering first")
    @PreAuthorize("@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'PRODUCER')")
    public ResponseEntity<com.stemotion.domain.script.dto.ScriptDtos.ScriptResponse> submitVideoReview(
            @PathVariable String projectId, @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(feedbackService.submitForVideoReview(projectId, principal.getUsername()));
    }

    @PostMapping("/qa-comments/{commentId}/replies")
    @Operation(summary = "Reply under a review note")
    @PreAuthorize("@workspaceAccess.hasCommentRole(#commentId, authentication.name, 'WRITER', 'PRODUCER', 'REVIEWER')")
    public ResponseEntity<FeedbackResponse> reply(@PathVariable String commentId,
            @Valid @RequestBody com.stemotion.domain.review.dto.FeedbackDtos.ReplyRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(feedbackService.reply(commentId, request.content(), principal.getUsername()));
    }

    @PostMapping("/scripts/{projectId}/qa-comments")
    @Operation(summary = "Pin a note at a timecode or on a scene")
    public ResponseEntity<FeedbackResponse> create(
        @PathVariable String projectId,
        @Valid @RequestBody FeedbackRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        FeedbackResponse created = feedbackService.create(projectId, request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/scenes/{sceneId}/comments")
    @Operation(summary = "Inline comments on one scene")
    public ResponseEntity<List<FeedbackResponse>> findByScene(@PathVariable String sceneId) {
        return ResponseEntity.ok(feedbackService.findByScene(sceneId));
    }

    /**
     * The writer (script notes, UC-26 Read & Resolve Feedback) or producer (clip notes) marks a
     * note fixed; the reviewer may also close it. Who and when are recorded.
     */
    @PutMapping("/qa-comments/{commentId}/resolve")
    @PreAuthorize("@workspaceAccess.hasCommentRole(#commentId, authentication.name, 'WRITER', 'PRODUCER', 'REVIEWER')")
    public ResponseEntity<FeedbackResponse> resolve(@PathVariable String commentId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(feedbackService.resolve(commentId, principal.getUsername()));
    }

    /** Only the reviewer decides that a fix was not good enough. */
    @PutMapping("/qa-comments/{commentId}/reopen")
    @PreAuthorize("@workspaceAccess.hasCommentRole(#commentId, authentication.name, 'REVIEWER')")
    public ResponseEntity<FeedbackResponse> reopen(@PathVariable String commentId) {
        return ResponseEntity.ok(feedbackService.reopen(commentId));
    }

    /**
     * Only the author (or an admin) removes a note. Open notes block video
     * approval, so letting others delete them would let a clip skip QA.
     */
    @DeleteMapping("/qa-comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable String commentId, @AuthenticationPrincipal UserDetails principal) {
        feedbackService.delete(commentId, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scripts/{projectId}/qa-report")
    @Operation(summary = "Run the automated pre-publish checks")
    public ResponseEntity<QaReport> qaReport(@PathVariable String projectId) {
        return ResponseEntity.ok(clipQaService.run(projectId));
    }

    @PostMapping("/scripts/{projectId}/approve-video")
    @Operation(summary = "Sign off the rendered clip once every note is resolved")
    @PreAuthorize("@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'REVIEWER')")
    public ResponseEntity<Void> approveVideo(@PathVariable String projectId,
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody(required = false) com.stemotion.domain.script.dto.ScriptDtos.ReviewDecisionRequest request) {
        feedbackService.approveVideo(projectId, principal.getUsername(), request == null ? null : request.note());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/scripts/{projectId}/send-back")
    @Operation(summary = "Return the clip to the producer for a re-render")
    @PreAuthorize("@workspaceAccess.hasProjectRole(#projectId, authentication.name, 'REVIEWER')")
    public ResponseEntity<Void> sendBack(@PathVariable String projectId,
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody(required = false) com.stemotion.domain.script.dto.ScriptDtos.ReviewDecisionRequest request) {
        feedbackService.sendBackToProduction(projectId, principal.getUsername(), request == null ? null : request.note());
        return ResponseEntity.noContent().build();
    }
}
