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

    @GetMapping("/scripts/{projectId}/qa-comments")
    @Operation(summary = "Review notes pinned to a clip")
    public ResponseEntity<List<FeedbackResponse>> findByProject(
        @PathVariable String projectId,
        @RequestParam(required = false) FeedbackStatus status
    ) {
        return ResponseEntity.ok(feedbackService.findByProject(projectId, status));
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

    @PutMapping("/qa-comments/{commentId}/resolve")
    public ResponseEntity<FeedbackResponse> resolve(@PathVariable String commentId) {
        return ResponseEntity.ok(feedbackService.resolve(commentId));
    }

    @PutMapping("/qa-comments/{commentId}/reopen")
    public ResponseEntity<FeedbackResponse> reopen(@PathVariable String commentId) {
        return ResponseEntity.ok(feedbackService.reopen(commentId));
    }

    @DeleteMapping("/qa-comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable String commentId) {
        feedbackService.delete(commentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scripts/{projectId}/qa-report")
    @Operation(summary = "Run the automated pre-publish checks")
    public ResponseEntity<QaReport> qaReport(@PathVariable String projectId) {
        return ResponseEntity.ok(clipQaService.run(projectId));
    }

    @PostMapping("/scripts/{projectId}/approve-video")
    @Operation(summary = "Sign off the rendered clip once every note is resolved")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<Void> approveVideo(@PathVariable String projectId) {
        feedbackService.approveVideo(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/scripts/{projectId}/send-back")
    @Operation(summary = "Return the clip to the producer for a re-render")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<Void> sendBack(@PathVariable String projectId) {
        feedbackService.sendBackToProduction(projectId);
        return ResponseEntity.noContent().build();
    }
}
