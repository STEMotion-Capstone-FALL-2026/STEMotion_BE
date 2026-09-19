package com.stemotion.domain.script.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.entity.ProjectStatus;
import com.stemotion.domain.project.entity.StatusHistory;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import com.stemotion.domain.script.entity.CommentAnnotation;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.entity.ScriptVersionStatus;
import com.stemotion.domain.script.repository.CommentAnnotationRepository;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptReviewService {

    private final ClipProjectRepository clipProjectRepository;
    private final ScriptVersionRepository scriptVersionRepository;
    private final SceneRepository sceneRepository;
    private final CommentAnnotationRepository commentAnnotationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public ClipProject submitScriptForReview(String projectId, String userId) {
        ClipProject project = findProjectOrThrow(projectId);
        User user = findUserOrThrow(userId);

        validateStateTransition(project.getStatus(), ProjectStatus.SCRIPT_REVIEW);
        validateOwnership(project, userId);

        ScriptVersion latestVersion = findLatestVersionOrThrow(projectId);
        validateScriptHasScenes(latestVersion.getId());

        ProjectStatus fromStatus = project.getStatus();
        project.setStatus(ProjectStatus.SCRIPT_REVIEW);
        latestVersion.setStatus(ScriptVersionStatus.SUBMITTED);

        clipProjectRepository.save(project);
        scriptVersionRepository.save(latestVersion);
        recordStatusChange(project, fromStatus, ProjectStatus.SCRIPT_REVIEW, user, "Script submitted for review");

        log.info("Project [{}] submitted for review by user [{}]", projectId, userId);
        return project;
    }

    @Transactional
    public CommentAnnotation addInlineComment(String sceneId, String reviewerId, String content, String timecode) {
        Scene scene = sceneRepository.findById(sceneId)
            .orElseThrow(() -> ApiException.notFound("Scene", sceneId));

        findUserOrThrow(reviewerId);

        CommentAnnotation comment = CommentAnnotation.builder()
            .scene(scene)
            .content(content)
            .timecode(timecode)
            .status("Open")
            .build();

        CommentAnnotation saved = commentAnnotationRepository.save(comment);
        log.info("Inline comment added to scene [{}] by reviewer [{}]", sceneId, reviewerId);
        return saved;
    }

    @Transactional
    public ClipProject approveScript(String projectId, String reviewerId) {
        ClipProject project = findProjectOrThrow(projectId);
        User reviewer = findUserOrThrow(reviewerId);

        validateStateTransition(project.getStatus(), ProjectStatus.APPROVED);

        ScriptVersion latestVersion = findLatestVersionOrThrow(projectId);
        if (latestVersion.getStatus() != ScriptVersionStatus.SUBMITTED) {
            throw ApiException.badRequest("Cannot approve: script version is not in SUBMITTED status");
        }

        ProjectStatus fromStatus = project.getStatus();
        project.setStatus(ProjectStatus.APPROVED);
        latestVersion.setStatus(ScriptVersionStatus.APPROVED);

        clipProjectRepository.save(project);
        scriptVersionRepository.save(latestVersion);
        recordStatusChange(project, fromStatus, ProjectStatus.APPROVED, reviewer, "Script approved by reviewer");

        log.info("Project [{}] approved by reviewer [{}]", projectId, reviewerId);
        return project;
    }

    @Transactional
    public ClipProject returnScriptWithChanges(String projectId, String reviewerId, String feedbackNote) {
        ClipProject project = findProjectOrThrow(projectId);
        User reviewer = findUserOrThrow(reviewerId);

        if (project.getStatus() != ProjectStatus.SCRIPT_REVIEW) {
            throw ApiException.badRequest(
                "Cannot return script: project is not in SCRIPT_REVIEW status (current: " + project.getStatus() + ")"
            );
        }

        ScriptVersion latestVersion = findLatestVersionOrThrow(projectId);

        ProjectStatus fromStatus = project.getStatus();
        project.setStatus(ProjectStatus.DRAFT);
        latestVersion.setStatus(ScriptVersionStatus.CHANGES_REQUESTED);

        clipProjectRepository.save(project);
        scriptVersionRepository.save(latestVersion);
        recordStatusChange(project, fromStatus, ProjectStatus.DRAFT, reviewer, feedbackNote);

        log.info("Project [{}] returned with changes by reviewer [{}]: {}", projectId, reviewerId, feedbackNote);
        return project;
    }

    // --- Private helpers ---

    private ClipProject findProjectOrThrow(String projectId) {
        return clipProjectRepository.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("ClipProject", projectId));
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("User", userId));
    }

    private ScriptVersion findLatestVersionOrThrow(String projectId) {
        List<ScriptVersion> versions = scriptVersionRepository
            .findByProjectIdOrderByVersionNoDesc(projectId);

        if (versions.isEmpty()) {
            throw ApiException.badRequest("No script version found for project: " + projectId);
        }
        return versions.get(0);
    }

    private void validateOwnership(ClipProject project, String userId) {
        if (!project.getOwner().getId().equals(userId)) {
            throw ApiException.badRequest("Only the project owner can submit for review");
        }
    }

    private void validateScriptHasScenes(String versionId) {
        List<Scene> scenes = sceneRepository.findByScriptVersionIdOrderByOrderNoAsc(versionId);
        if (scenes.isEmpty()) {
            throw ApiException.badRequest("Cannot submit: script version has no scenes");
        }
    }

    private void validateStateTransition(ProjectStatus current, ProjectStatus target) {
        boolean valid = switch (target) {
            case SCRIPT_REVIEW -> current == ProjectStatus.DRAFT;
            case APPROVED -> current == ProjectStatus.SCRIPT_REVIEW;
            case IN_PRODUCTION -> current == ProjectStatus.APPROVED;
            case CLIP_REVIEW -> current == ProjectStatus.IN_PRODUCTION;
            case PUBLISHED -> current == ProjectStatus.CLIP_REVIEW;
            case ARCHIVED -> true;
            default -> false;
        };

        if (!valid) {
            throw ApiException.badRequest(
                String.format("Invalid state transition: %s → %s", current, target)
            );
        }
    }

    private void recordStatusChange(ClipProject project, ProjectStatus from, ProjectStatus to, User changedBy, String note) {
        StatusHistory history = StatusHistory.builder()
            .project(project)
            .fromStatus(from)
            .toStatus(to)
            .note(note)
            .changedBy(changedBy)
            .build();
        statusHistoryRepository.save(history);
    }
}
