package com.stemotion.domain.review.service;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.Composition;
import com.stemotion.domain.production.service.ClipArtifactService;
import com.stemotion.domain.production.service.CompositionService;
import com.stemotion.domain.production.service.RenderService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.entity.StatusHistory;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.review.dto.FeedbackDtos.FeedbackRequest;
import com.stemotion.domain.review.dto.FeedbackDtos.FeedbackResponse;
import com.stemotion.domain.review.entity.FeedbackComment;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptResponse;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Clip QA: timestamped review notes, their replies, and the video approval gate.
 *
 * <p>What a reviewer signs off is either a rendered Clip or, in the web-first flow (FE checklist
 * BE-P0-5), the composition revision shown in the browser player before any MP4 exists. The
 * composition is frozen from submission to decision, and the MP4 rendered after approval draws
 * that same revision.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackCommentRepository feedbackRepository;
    private final SceneRepository sceneRepository;
    private final ClipProjectRepository projectRepository;
    private final ScriptService scriptService;
    private final AuthService authService;
    private final ClipArtifactService clipArtifactService;
    private final StatusHistoryService statusHistoryService;
    private final CompositionService compositionService;
    private final RenderService renderService;

    /**
     * Notes of a project. A status filter applies to the notes themselves; their replies come
     * along. A clipId keeps the notes written on that render plus the unscoped ones.
     */
    @Transactional(readOnly = true)
    public List<FeedbackResponse> findByProject(String projectId, FeedbackStatus status, String clipId) {
        scriptService.getOrThrow(projectId);
        // One query with the people and scene each response shows (DB performance plan 2.7).
        List<FeedbackComment> all = feedbackRepository.findForProjectWithPeople(projectId);
        java.util.Set<String> kept = new java.util.HashSet<>();
        List<FeedbackResponse> result = new ArrayList<>();
        for (FeedbackComment comment : all) {
            if (comment.getParent() != null) continue;
            if (status != null && comment.getStatus() != status) continue;
            if (clipId != null && comment.getClipId() != null && !clipId.equals(comment.getClipId())) continue;
            kept.add(comment.getId());
            result.add(FeedbackResponse.from(comment));
        }
        for (FeedbackComment comment : all) {
            if (comment.getParent() != null && kept.contains(comment.getParent().getId())) {
                result.add(FeedbackResponse.from(comment));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> findByProject(String projectId, FeedbackStatus status) {
        return findByProject(projectId, status, null);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> findByScene(String sceneId) {
        return feedbackRepository.findForSceneWithPeople(sceneId)
            .stream()
            .map(FeedbackResponse::from)
            .toList();
    }

    @Transactional
    public FeedbackResponse create(String projectId, FeedbackRequest request, String authorId) {
        ClipProject project = scriptService.getOrThrow(projectId);

        Scene scene = null;
        if (request.sceneId() != null) {
            scene = sceneRepository.findById(request.sceneId())
                .orElseThrow(() -> ApiException.notFound("Scene", request.sceneId()));
        }

        if (request.timestampSec() != null && request.timestampSec() < 0) {
            throw ApiException.badRequest("timestampSec cannot be negative");
        }
        if (scene != null && !scene.getScriptVersion().getProject().getId().equals(projectId)) {
            throw ApiException.badRequest("The scene does not belong to this project");
        }
        if (scene == null && request.timestampSec() == null) {
            throw ApiException.badRequest("A comment requires a scene or a timestamp");
        }

        // A pin in the video belongs to the render being watched, when there is one.
        String clipId = scene != null ? null
            : currentClip(project, scriptService.currentVersion(project)).map(Clip::getId).orElse(null);

        FeedbackComment comment = feedbackRepository.save(FeedbackComment.builder()
            .project(project)
            .scene(scene)
            .author(authService.findUserOrThrow(authorId))
            .timestampSec(request.timestampSec())
            .content(request.content())
            .tag(request.tag())
            .clipId(clipId)
            .status(FeedbackStatus.OPEN)
            .build());

        return FeedbackResponse.from(comment);
    }

    /** A reply under a note (BE-P1-2); a reply to a reply joins the same thread. */
    @Transactional
    public FeedbackResponse reply(String commentId, String content, String authorId) {
        FeedbackComment parent = getOrThrow(commentId);
        FeedbackComment root = parent.getParent() == null ? parent : parent.getParent();
        FeedbackComment reply = feedbackRepository.save(FeedbackComment.builder()
            .project(root.getProject())
            .scene(root.getScene())
            .timestampSec(root.getTimestampSec())
            .clipId(root.getClipId())
            .parent(root)
            .author(authService.findUserOrThrow(authorId))
            .content(content.trim())
            .status(FeedbackStatus.OPEN)
            .build());
        return FeedbackResponse.from(reply);
    }

    @Transactional
    public FeedbackResponse resolve(String commentId, String actorId) {
        FeedbackComment comment = requireNote(commentId);
        comment.setStatus(FeedbackStatus.RESOLVED);
        comment.setResolvedBy(authService.findUserOrThrow(actorId));
        comment.setResolvedAt(java.time.LocalDateTime.now());
        return FeedbackResponse.from(feedbackRepository.save(comment));
    }

    @Transactional
    public FeedbackResponse resolve(String commentId) {
        FeedbackComment comment = requireNote(commentId);
        comment.setStatus(FeedbackStatus.RESOLVED);
        return FeedbackResponse.from(feedbackRepository.save(comment));
    }

    @Transactional
    public FeedbackResponse reopen(String commentId) {
        FeedbackComment comment = requireNote(commentId);
        comment.setStatus(FeedbackStatus.OPEN);
        comment.setResolvedBy(null);
        comment.setResolvedAt(null);
        return FeedbackResponse.from(feedbackRepository.save(comment));
    }

    @Transactional
    public void delete(String commentId, String actorId) {
        FeedbackComment comment = getOrThrow(commentId);
        User actor = authService.findUserOrThrow(actorId);
        boolean author = comment.getAuthor() != null && comment.getAuthor().getId().equals(actorId);
        if (!author && actor.getRole() != User.UserRole.ADMIN) {
            throw ApiException.forbidden("Only the author of a note can delete it");
        }
        feedbackRepository.delete(comment);
    }

    /**
     * The producer hands the composition to the reviewer without rendering first (BE-P0-5). The
     * composition (revision 1 from the approved script if never edited) is frozen until the
     * reviewer approves it or sends it back.
     */
    @Transactional
    public ScriptResponse submitForVideoReview(String projectId, String producerId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        scriptService.requireActive(project);
        if (project.getScriptStatus() != ScriptStatus.APPROVED) {
            throw ApiException.badRequest("Only a project with an approved script can be sent for clip review");
        }
        if (project.getVideoStatus() != VideoStatus.NOT_RENDERED && project.getVideoStatus() != VideoStatus.RENDERING) {
            throw ApiException.badRequest("Clip is " + project.getVideoStatus() + "; it is already in review or approved");
        }
        ScriptVersion version = scriptService.currentVersion(project);
        Composition composition = compositionService.ensure(project, version, producerId);
        var from = project.getPipelineStatus();
        project.setVideoStatus(VideoStatus.IN_QA);
        projectRepository.save(project);
        statusHistoryService.record(project, from, authService.findUserOrThrow(producerId),
            "Gửi duyệt trên web player: composition revision " + composition.getRevision());
        return scriptService.findById(projectId);
    }

    /**
     * Signs off the clip. Refuses while notes on it are still open so a clip cannot reach
     * publishing with unresolved QA findings. Without a rendered Clip of the reviewed revision,
     * the composition revision is what is approved and its MP4 is rendered next.
     */
    @Transactional
    public void approveVideo(String projectId, String reviewerId, String reason) {
        ClipProject project = scriptService.getOrThrow(projectId);
        scriptService.requireActive(project);

        if (project.getVideoStatus() != VideoStatus.IN_QA) {
            throw ApiException.badRequest(
                "Clip is " + project.getVideoStatus() + "; only a clip in QA can be approved"
            );
        }

        ScriptVersion version = scriptService.currentVersion(project);
        Optional<Clip> clip = currentClip(project, version);
        long openNotes = openNotes(projectId, clip.orElse(null));
        if (openNotes > 0) {
            throw ApiException.badRequest(
                "Resolve the " + openNotes + " open review note(s) before approving the clip"
            );
        }

        var from = project.getPipelineStatus();
        project.setVideoStatus(VideoStatus.APPROVED);
        projectRepository.save(project);
        User reviewer = authService.findUserOrThrow(reviewerId);
        if (clip.isPresent()) {
            statusHistoryService.recordReview(project, from, reviewer,
                StatusHistory.TargetType.CLIP, clip.get().getId(), StatusHistory.Decision.APPROVED, reason);
            return;
        }
        Composition composition = compositionService.ensure(project, version, reviewerId);
        statusHistoryService.recordReview(project, from, reviewer, StatusHistory.TargetType.COMPOSITION,
            composition.getId(), StatusHistory.Decision.APPROVED, reason, revisionNote(composition));
        renderService.renderApproved(projectId, reviewerId);
    }

    /** Sends the clip, or the composition reviewed in the web player, back to the producer. */
    @Transactional
    public void sendBackToProduction(String projectId, String reviewerId, String note) {
        String reason = ScriptService.requireReason(note);
        ClipProject project = scriptService.getOrThrow(projectId);
        scriptService.requireActive(project);
        if (project.getVideoStatus() != VideoStatus.IN_QA) {
            throw ApiException.badRequest("Only a clip in QA can be sent back to production");
        }
        ScriptVersion version = scriptService.currentVersion(project);
        Optional<Clip> clip = currentClip(project, version);
        var from = project.getPipelineStatus();
        project.setVideoStatus(VideoStatus.NOT_RENDERED);
        projectRepository.save(project);
        User reviewer = authService.findUserOrThrow(reviewerId);
        if (clip.isPresent()) {
            statusHistoryService.recordReview(project, from, reviewer,
                StatusHistory.TargetType.CLIP, clip.get().getId(), StatusHistory.Decision.REJECTED, reason);
        } else {
            Composition composition = compositionService.ensure(project, version, reviewerId);
            statusHistoryService.recordReview(project, from, reviewer, StatusHistory.TargetType.COMPOSITION,
                composition.getId(), StatusHistory.Decision.REJECTED, reason, revisionNote(composition));
        }
    }

    /**
     * The Clip that shows the composition under review: the current render, provided it was made
     * from the current composition revision. A render of an earlier revision does not count.
     */
    private Optional<Clip> currentClip(ClipProject project, ScriptVersion version) {
        if (clipArtifactService == null) return Optional.empty();
        Optional<Clip> clip = clipArtifactService.findCurrent(project, version.getId());
        if (clip.isEmpty()) return clip;
        Integer rendered = clip.get().getRenderJob() == null ? null : clip.get().getRenderJob().getCompositionRevision();
        Integer current = compositionService.renderInput(version).revision();
        return Objects.equals(rendered, current) ? clip : Optional.empty();
    }

    /** Open notes that block approval: the current clip's and the unscoped ones, never replies. */
    private long openNotes(String projectId, Clip clip) {
        long unscoped = feedbackRepository.countByProjectIdAndStatusAndParentIsNullAndClipIdIsNull(
            projectId, FeedbackStatus.OPEN);
        return clip == null ? unscoped : unscoped
            + feedbackRepository.countByProjectIdAndStatusAndParentIsNullAndClipId(projectId, FeedbackStatus.OPEN, clip.getId());
    }

    static String revisionNote(Composition composition) {
        return "compositionRevision=" + composition.getRevision();
    }

    private FeedbackComment requireNote(String commentId) {
        FeedbackComment comment = getOrThrow(commentId);
        if (comment.getParent() != null) {
            throw ApiException.badRequest("A reply is not resolved on its own; resolve the note it answers");
        }
        return comment;
    }

    private FeedbackComment getOrThrow(String commentId) {
        return feedbackRepository.findById(commentId)
            .orElseThrow(() -> ApiException.notFound("FeedbackComment", commentId));
    }
}
