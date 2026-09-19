package com.stemotion.domain.script.service;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import com.stemotion.domain.script.dto.ScriptDtos.CreateScriptRequest;
import com.stemotion.domain.script.dto.ScriptDtos.ReviewDecisionRequest;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptResponse;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptSummaryResponse;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptVersionResponse;
import com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest;
import com.stemotion.domain.script.dto.ScriptDtos.VersionDiffResponse;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.workspace.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the editorial half of the pipeline: Draft → In review → Approved,
 * with a change-request loop back to Draft. Each resubmission produces a new
 * ScriptVersion so reviewers can compare iterations.
 */
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ClipProjectRepository projectRepository;
    private final ScriptVersionRepository versionRepository;
    private final SceneRepository sceneRepository;
    private final SceneMapper sceneMapper;
    private final AuthService authService;
    private final WorkspaceService workspaceService;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<ScriptSummaryResponse> findByWorkspace(String workspaceId) {
        workspaceService.getOrThrow(workspaceId);
        return projectRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
            .stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ScriptSummaryResponse> findByOwner(String ownerId) {
        return projectRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
            .stream()
            .map(this::toSummary)
            .toList();
    }

    /** The reviewer queue: everything currently awaiting an editorial decision. */
    @Transactional(readOnly = true)
    public List<ScriptSummaryResponse> findReviewQueue() {
        return projectRepository.findByScriptStatusOrderByCreatedAtDesc(ScriptStatus.IN_REVIEW)
            .stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public ScriptResponse findById(String projectId) {
        ClipProject project = getOrThrow(projectId);
        ScriptVersion current = currentVersion(project);
        return toResponse(project, current, scenesOf(current));
    }

    @Transactional(readOnly = true)
    public List<ScriptVersionResponse> findVersions(String projectId) {
        getOrThrow(projectId);
        return versionRepository.findByProjectIdOrderByVersionNoDesc(projectId)
            .stream()
            .map(ScriptService::toVersionResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public VersionDiffResponse diffVersions(String projectId, String leftVersionId, String rightVersionId) {
        getOrThrow(projectId);
        ScriptVersion left = versionOrThrow(leftVersionId, projectId);
        ScriptVersion right = versionOrThrow(rightVersionId, projectId);
        return new VersionDiffResponse(
            toVersionResponse(left),
            sceneMapper.toResponses(scenesOf(left)),
            toVersionResponse(right),
            sceneMapper.toResponses(scenesOf(right))
        );
    }

    // --------------------------------------------------------------- commands

    @Transactional
    public ScriptResponse create(String workspaceId, CreateScriptRequest request, String currentUserId) {
        User author = authService.findUserOrThrow(currentUserId);

        ClipProject project = ClipProject.builder()
            .title(request.title())
            .subject(request.subject())
            .gradeLevel(request.gradeLevel())
            .fps(request.fps() == null ? 30 : request.fps())
            .scriptStatus(ScriptStatus.DRAFT)
            .videoStatus(VideoStatus.NOT_RENDERED)
            .owner(author)
            .workspace(workspaceService.getOrThrow(workspaceId))
            .build();
        project = projectRepository.save(project);

        ScriptVersion version = versionRepository.save(ScriptVersion.builder()
            .versionNo(1)
            .title(request.title())
            .status(ScriptStatus.DRAFT)
            .isCurrent(true)
            .project(project)
            .createdBy(author)
            .build());

        List<Scene> scenes = replaceScenes(version, request.scenes());
        applyTotalDuration(project, scenes);

        return toResponse(project, version, scenes);
    }

    /**
     * Saves the working draft in place. An approved script is frozen: editing it
     * would invalidate the review, so callers must submit a new version instead.
     */
    @Transactional
    public ScriptResponse updateDraft(String projectId, UpdateScriptRequest request) {
        ClipProject project = getOrThrow(projectId);
        if (project.getScriptStatus() == ScriptStatus.APPROVED) {
            throw ApiException.badRequest(
                "An approved script cannot be edited; create a new version to make changes"
            );
        }

        if (request.title() != null) {
            project.setTitle(request.title());
        }
        if (request.subject() != null) {
            project.setSubject(request.subject());
        }
        if (request.gradeLevel() != null) {
            project.setGradeLevel(request.gradeLevel());
        }
        if (request.fps() != null) {
            project.setFps(request.fps());
        }

        ScriptVersion current = currentVersion(project);
        if (request.title() != null) {
            current.setTitle(request.title());
        }
        if (request.summary() != null) {
            current.setSummary(request.summary());
        }

        List<Scene> scenes = request.scenes() == null
            ? scenesOf(current)
            : replaceScenes(current, request.scenes());

        versionRepository.save(current);
        applyTotalDuration(project, scenes);

        return toResponse(project, current, scenes);
    }

    /** Writer hands the draft to the reviewer queue. */
    @Transactional
    public ScriptResponse submitForReview(String projectId) {
        ClipProject project = getOrThrow(projectId);
        requireStatus(project, ScriptStatus.DRAFT, ScriptStatus.CHANGE_REQUESTED);

        ScriptVersion current = currentVersion(project);
        List<Scene> scenes = scenesOf(current);
        if (scenes.isEmpty()) {
            throw ApiException.badRequest("A script needs at least one scene before it can be reviewed");
        }

        project.setScriptStatus(ScriptStatus.IN_REVIEW);
        current.setStatus(ScriptStatus.IN_REVIEW);
        versionRepository.save(current);
        projectRepository.save(project);

        return toResponse(project, current, scenes);
    }

    /** Reviewer accepts the script; it becomes available to the producer. */
    @Transactional
    public ScriptResponse approve(String projectId, ReviewDecisionRequest request) {
        ClipProject project = getOrThrow(projectId);
        requireStatus(project, ScriptStatus.IN_REVIEW);

        ScriptVersion current = currentVersion(project);
        current.setStatus(ScriptStatus.APPROVED);
        current.setReviewNote(request == null ? null : request.note());
        project.setScriptStatus(ScriptStatus.APPROVED);

        versionRepository.save(current);
        projectRepository.save(project);

        return toResponse(project, current, scenesOf(current));
    }

    /**
     * Reviewer returns the script. A fresh version is opened and seeded with a
     * copy of the reviewed scenes, so the writer edits a new revision while the
     * reviewed one stays intact for the diff view.
     */
    @Transactional
    public ScriptResponse requestChanges(String projectId, ReviewDecisionRequest request, String reviewerId) {
        ClipProject project = getOrThrow(projectId);
        requireStatus(project, ScriptStatus.IN_REVIEW);

        ScriptVersion reviewed = currentVersion(project);
        reviewed.setStatus(ScriptStatus.CHANGE_REQUESTED);
        reviewed.setReviewNote(request == null ? null : request.note());
        reviewed.setIsCurrent(false);
        versionRepository.save(reviewed);

        ScriptVersion next = versionRepository.save(ScriptVersion.builder()
            .versionNo(reviewed.getVersionNo() + 1)
            .title(reviewed.getTitle())
            .summary(reviewed.getSummary())
            .status(ScriptStatus.CHANGE_REQUESTED)
            .reviewNote(request == null ? null : request.note())
            .isCurrent(true)
            .project(project)
            .createdBy(authService.findUserOrThrow(reviewerId))
            .build());

        List<Scene> copied = copyScenes(scenesOf(reviewed), next);
        project.setScriptStatus(ScriptStatus.CHANGE_REQUESTED);
        projectRepository.save(project);

        return toResponse(project, next, copied);
    }

    @Transactional
    public void delete(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw ApiException.notFound("ClipProject", projectId);
        }
        projectRepository.deleteById(projectId);
    }

    // ------------------------------------------------------------- shared API

    @Transactional(readOnly = true)
    public ClipProject getOrThrow(String projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("ClipProject", projectId));
    }

    @Transactional(readOnly = true)
    public ScriptVersion currentVersion(ClipProject project) {
        return versionRepository.findFirstByProjectIdAndIsCurrentTrue(project.getId())
            .or(() -> versionRepository.findFirstByProjectIdOrderByVersionNoDesc(project.getId()))
            .orElseThrow(() -> ApiException.notFound("ScriptVersion for project", project.getId()));
    }

    public List<Scene> scenesOf(ScriptVersion version) {
        return sceneRepository.findByScriptVersionIdOrderByOrderNoAsc(version.getId());
    }

    public ScriptResponse toResponse(ClipProject project, ScriptVersion version, List<Scene> scenes) {
        List<SceneResponse> sceneResponses = sceneMapper.toResponses(scenes);
        return new ScriptResponse(
            project.getId(),
            project.getTitle(),
            project.getSubject(),
            project.getGradeLevel(),
            project.getTotalDurationSeconds(),
            project.getScriptStatus(),
            project.getVideoStatus(),
            project.getFps(),
            sceneResponses,
            project.getWorkspace().getId(),
            project.getOwner().getId(),
            project.getOwner().getFullName(),
            version.getVersionNo(),
            project.getVideoUrl(),
            project.getCreatedAt()
        );
    }

    // --------------------------------------------------------------- internals

    private List<Scene> replaceScenes(ScriptVersion version, List<SceneRequest> requests) {
        sceneRepository.deleteByScriptVersionId(version.getId());
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Scene> scenes = new java.util.ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            scenes.add(sceneMapper.toEntity(requests.get(i), i + 1, version));
        }
        return sceneRepository.saveAll(scenes);
    }

    private List<Scene> copyScenes(List<Scene> source, ScriptVersion target) {
        List<Scene> copies = source.stream()
            .map(scene -> Scene.builder()
                .orderNo(scene.getOrderNo())
                .sceneType(scene.getSceneType())
                .title(scene.getTitle())
                .narration(scene.getNarration())
                .durationInFrames(scene.getDurationInFrames())
                .props(scene.getProps())
                .scriptVersion(target)
                .build())
            .toList();
        return sceneRepository.saveAll(copies);
    }

    private void applyTotalDuration(ClipProject project, List<Scene> scenes) {
        int fps = project.getFps() == null || project.getFps() <= 0 ? 30 : project.getFps();
        int frames = scenes.stream()
            .mapToInt(scene -> scene.getDurationInFrames() == null ? 0 : scene.getDurationInFrames())
            .sum();
        project.setTotalDurationSeconds(Math.round((float) frames / fps));
        projectRepository.save(project);
    }

    private ScriptVersion versionOrThrow(String versionId, String projectId) {
        ScriptVersion version = versionRepository.findById(versionId)
            .orElseThrow(() -> ApiException.notFound("ScriptVersion", versionId));
        if (!version.getProject().getId().equals(projectId)) {
            throw ApiException.badRequest("Version " + versionId + " does not belong to project " + projectId);
        }
        return version;
    }

    private void requireStatus(ClipProject project, ScriptStatus... allowed) {
        for (ScriptStatus status : allowed) {
            if (project.getScriptStatus() == status) {
                return;
            }
        }
        throw ApiException.badRequest(
            "Script is " + project.getScriptStatus() + "; this transition is not allowed from that state"
        );
    }

    private ScriptSummaryResponse toSummary(ClipProject project) {
        ScriptVersion current = currentVersion(project);
        return new ScriptSummaryResponse(
            project.getId(),
            project.getTitle(),
            project.getSubject(),
            project.getGradeLevel(),
            project.getTotalDurationSeconds(),
            project.getScriptStatus(),
            project.getVideoStatus(),
            project.getOwner().getFullName(),
            sceneRepository.findByScriptVersionIdOrderByOrderNoAsc(current.getId()).size(),
            project.getUpdatedAt()
        );
    }

    private static ScriptVersionResponse toVersionResponse(ScriptVersion version) {
        return new ScriptVersionResponse(
            version.getId(),
            version.getVersionNo(),
            version.getTitle(),
            version.getSummary(),
            version.getStatus(),
            version.getReviewNote(),
            version.getIsCurrent(),
            version.getCreatedBy().getId(),
            version.getCreatedBy().getFullName(),
            version.getCreatedAt()
        );
    }
}
