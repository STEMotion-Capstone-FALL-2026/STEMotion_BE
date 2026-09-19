package com.stemotion.domain.review.service;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.review.dto.FeedbackDtos.FeedbackRequest;
import com.stemotion.domain.review.dto.FeedbackDtos.FeedbackResponse;
import com.stemotion.domain.review.entity.FeedbackComment;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Clip QA: timestamped review notes and the video approval gate.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackCommentRepository feedbackRepository;
    private final SceneRepository sceneRepository;
    private final ClipProjectRepository projectRepository;
    private final ScriptService scriptService;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public List<FeedbackResponse> findByProject(String projectId, FeedbackStatus status) {
        scriptService.getOrThrow(projectId);
        List<FeedbackComment> comments = status == null
            ? feedbackRepository.findByProjectIdOrderByTimestampSecAsc(projectId)
            : feedbackRepository.findByProjectIdAndStatusOrderByTimestampSecAsc(projectId, status);
        return comments.stream().map(FeedbackResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> findByScene(String sceneId) {
        return feedbackRepository.findBySceneIdOrderByCreatedAtAsc(sceneId)
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

        FeedbackComment comment = feedbackRepository.save(FeedbackComment.builder()
            .project(project)
            .scene(scene)
            .author(authService.findUserOrThrow(authorId))
            .timestampSec(request.timestampSec())
            .content(request.content())
            .tag(request.tag())
            .status(FeedbackStatus.OPEN)
            .build());

        return FeedbackResponse.from(comment);
    }

    @Transactional
    public FeedbackResponse resolve(String commentId) {
        FeedbackComment comment = getOrThrow(commentId);
        comment.setStatus(FeedbackStatus.RESOLVED);
        return FeedbackResponse.from(feedbackRepository.save(comment));
    }

    @Transactional
    public FeedbackResponse reopen(String commentId) {
        FeedbackComment comment = getOrThrow(commentId);
        comment.setStatus(FeedbackStatus.OPEN);
        return FeedbackResponse.from(feedbackRepository.save(comment));
    }

    @Transactional
    public void delete(String commentId) {
        feedbackRepository.delete(getOrThrow(commentId));
    }

    /**
     * Signs off the rendered clip. Refuses while notes are still open so a clip
     * cannot reach publishing with unresolved QA findings.
     */
    @Transactional
    public void approveVideo(String projectId) {
        ClipProject project = scriptService.getOrThrow(projectId);

        if (project.getVideoStatus() != VideoStatus.IN_QA) {
            throw ApiException.badRequest(
                "Clip is " + project.getVideoStatus() + "; only a clip in QA can be approved"
            );
        }

        long openNotes = feedbackRepository.countByProjectIdAndStatus(projectId, FeedbackStatus.OPEN);
        if (openNotes > 0) {
            throw ApiException.badRequest(
                "Resolve the " + openNotes + " open review note(s) before approving the clip"
            );
        }

        project.setVideoStatus(VideoStatus.APPROVED);
        projectRepository.save(project);
    }

    /** Sends the clip back to the producer for a re-render. */
    @Transactional
    public void sendBackToProduction(String projectId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        if (project.getVideoStatus() != VideoStatus.IN_QA) {
            throw ApiException.badRequest("Only a clip in QA can be sent back to production");
        }
        project.setVideoStatus(VideoStatus.NOT_RENDERED);
        projectRepository.save(project);
    }

    private FeedbackComment getOrThrow(String commentId) {
        return feedbackRepository.findById(commentId)
            .orElseThrow(() -> ApiException.notFound("FeedbackComment", commentId));
    }
}
