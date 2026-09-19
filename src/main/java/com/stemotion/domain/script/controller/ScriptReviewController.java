package com.stemotion.domain.script.controller;

import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.dto.FeedbackRequest;
import com.stemotion.domain.script.dto.InlineCommentRequest;
import com.stemotion.domain.script.dto.ReviewActionRequest;
import com.stemotion.domain.script.entity.CommentAnnotation;
import com.stemotion.domain.script.service.ScriptReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Script Review", description = "APIs for submitting and reviewing scripts")
public class ScriptReviewController {

    private final ScriptReviewService scriptReviewService;

    @Operation(summary = "Submit a script for review", description = "Transitions a DRAFT project to SCRIPT_REVIEW")
    @PostMapping("/{projectId}/submit-review")
    public ResponseEntity<ClipProject> submitForReview(
            @PathVariable String projectId,
            @Valid @RequestBody ReviewActionRequest request) {
        ClipProject updatedProject = scriptReviewService.submitScriptForReview(projectId, request.getUserId());
        return ResponseEntity.ok(updatedProject);
    }

    @Operation(summary = "Approve a script", description = "Transitions a SCRIPT_REVIEW project to APPROVED")
    @PostMapping("/{projectId}/approve")
    public ResponseEntity<ClipProject> approveScript(
            @PathVariable String projectId,
            @Valid @RequestBody ReviewActionRequest request) {
        ClipProject updatedProject = scriptReviewService.approveScript(projectId, request.getUserId());
        return ResponseEntity.ok(updatedProject);
    }

    @Operation(summary = "Return a script with changes", description = "Transitions a SCRIPT_REVIEW project back to DRAFT")
    @PostMapping("/{projectId}/return")
    public ResponseEntity<ClipProject> returnScript(
            @PathVariable String projectId,
            @Valid @RequestBody FeedbackRequest request) {
        ClipProject updatedProject = scriptReviewService.returnScriptWithChanges(projectId, request.getReviewerId(), request.getFeedbackNote());
        return ResponseEntity.ok(updatedProject);
    }

    @Operation(summary = "Add an inline comment to a scene", description = "Adds a comment annotation to a specific scene")
    @PostMapping("/scenes/{sceneId}/comments")
    public ResponseEntity<CommentAnnotation> addComment(
            @PathVariable String sceneId,
            @Valid @RequestBody InlineCommentRequest request) {
        CommentAnnotation comment = scriptReviewService.addInlineComment(
                sceneId, request.getReviewerId(), request.getContent(), request.getTimecode());
        return ResponseEntity.ok(comment);
    }
}
