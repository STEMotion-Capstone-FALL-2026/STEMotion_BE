package com.stemotion.domain.script.service;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.entity.ProjectStatus;
import com.stemotion.domain.project.service.StatusHistoryService;
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
    private final StatusHistoryService statusHistoryService;
    private final com.stemotion.core.security.WorkspaceAccess workspaceAccess;

    /** Read-only lookup of the production theme; optional so unit tests can build the service bare. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.stemotion.domain.production.repository.CompositionRepository compositionRepository;

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<ScriptSummaryResponse> findByWorkspace(String workspaceId) {
        workspaceService.getOrThrow(workspaceId);
        return projectRepository.findSummariesByWorkspace(workspaceId).stream().map(ScriptService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<ScriptSummaryResponse> findByOwner(String ownerId) {
        // Leaving a workspace removes access to all of its projects, own ones included.
        return projectRepository.findSummariesByOwner(ownerId, workspaceAccess.isAdmin(ownerId),
                com.stemotion.core.enums.StemEnums.MemberStatus.ACTIVE)
            .stream().map(ScriptService::toSummary).toList();
    }

    /**
     * The reviewer queue: scripts awaiting a decision in the workspaces where
     * the user is a reviewer. Workspaces where they only write are left out,
     * since they could not decide on those scripts anyway.
     */
    @Transactional(readOnly = true)
    public List<ScriptSummaryResponse> findReviewQueue(String userId) {
        return projectRepository.findReviewQueueSummaries(userId, workspaceAccess.isAdmin(userId), ScriptStatus.IN_REVIEW,
                com.stemotion.core.enums.StemEnums.MemberStatus.ACTIVE, com.stemotion.domain.auth.entity.User.UserRole.REVIEWER)
            .stream().map(ScriptService::toSummary).toList();
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
        return versionRepository.findHistoryWithAuthor(projectId)
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
        requireActive(project);
        if (project.getScriptStatus() != ScriptStatus.DRAFT
            && project.getScriptStatus() != ScriptStatus.CHANGE_REQUESTED) {
            throw ApiException.badRequest(
                "Only a draft or returned script can be edited; submitted versions are immutable"
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
    /** Scene durations measured from TTS audio, written to an editable draft (BE-P0-2). */
    @Transactional
    public ScriptResponse applyMeasuredDurations(String projectId, java.util.Map<String, Integer> framesBySceneId) {
        ClipProject project = getOrThrow(projectId);
        requireActive(project);
        if (project.getScriptStatus() != ScriptStatus.DRAFT
            && project.getScriptStatus() != ScriptStatus.CHANGE_REQUESTED) {
            throw ApiException.badRequest("Only a draft or returned script can be edited; submitted versions are immutable");
        }
        ScriptVersion current = currentVersion(project);
        List<Scene> scenes = sceneRepository.findForUpdateByScriptVersionId(current.getId());
        for (Scene scene : scenes) {
            Integer frames = framesBySceneId.get(scene.getId());
            if (frames != null && frames >= 1 && frames <= 18_000) scene.setDurationInFrames(frames);
        }
        sceneRepository.saveAll(scenes);
        applyTotalDuration(project, scenes);
        return toResponse(project, current, scenes);
    }

    @Transactional
    public ScriptResponse submitForReview(String projectId, String currentUserId) {
        ClipProject project = getOrThrow(projectId);
        requireStatus(project, ScriptStatus.DRAFT, ScriptStatus.CHANGE_REQUESTED);
        workspaceAccess.requireProjectRole(projectId, currentUserId, java.util.Set.of(User.UserRole.WRITER));

        ScriptVersion current = currentVersion(project);
        List<Scene> scenes = scenesOf(current);
        if (scenes.isEmpty()) {
            throw ApiException.badRequest("A script needs at least one scene before it can be reviewed");
        }

        ProjectStatus from = project.getPipelineStatus();
        statusHistoryService.validateTransition(from, ProjectStatus.SCRIPT_REVIEW);

        project.setScriptStatus(ScriptStatus.IN_REVIEW);
        current.setStatus(ScriptStatus.IN_REVIEW);
        versionRepository.save(current);
        projectRepository.save(project);
        statusHistoryService.record(
            project, from, authService.findUserOrThrow(currentUserId), "Script submitted for review");

        return toResponse(project, current, scenes);
    }

    /** Reviewer accepts the script; it becomes available to the producer. */
    @Transactional
    public ScriptResponse approve(String projectId, ReviewDecisionRequest request, String reviewerId) {
        ClipProject project = getOrThrow(projectId);
        requireStatus(project, ScriptStatus.IN_REVIEW);

        ProjectStatus from = project.getPipelineStatus();
        statusHistoryService.validateTransition(from, ProjectStatus.APPROVED);

        ScriptVersion current = currentVersion(project);
        current.setStatus(ScriptStatus.APPROVED);
        current.setReviewNote(request == null ? null : request.note());
        project.setScriptStatus(ScriptStatus.APPROVED);

        versionRepository.save(current);
        projectRepository.save(project);
        statusHistoryService.recordReview(
            project, from, authService.findUserOrThrow(reviewerId),
            com.stemotion.domain.project.entity.StatusHistory.TargetType.SCRIPT, current.getId(),
            com.stemotion.domain.project.entity.StatusHistory.Decision.APPROVED,
            request == null ? null : request.note());

        return toResponse(project, current, scenesOf(current));
    }

    /**
     * A return without a reason leaves the writer or producer guessing, so returning a script or a
     * clip needs one; it is stored with the decision and shown in the history.
     */
    public static String requireReason(String note) {
        if (note == null || note.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "REVIEW_REASON_REQUIRED",
                "Cần ghi lý do khi trả về (gửi {\"note\": \"...\"})");
        }
        return note.trim();
    }

    /**
     * Reviewer returns the script. A fresh version is opened and seeded with a
     * copy of the reviewed scenes, so the writer edits a new revision while the
     * reviewed one stays intact for the diff view. All of it commits together.
     */
    @Transactional
    public ScriptResponse requestChanges(String projectId, ReviewDecisionRequest request, String reviewerId) {
        String reason = requireReason(request == null ? null : request.note());
        ClipProject project = getOrThrow(projectId);
        requireStatus(project, ScriptStatus.IN_REVIEW);

        ProjectStatus from = project.getPipelineStatus();
        statusHistoryService.validateTransition(from, ProjectStatus.DRAFT);

        ScriptVersion reviewed = currentVersion(project);
        reviewed.setStatus(ScriptStatus.CHANGE_REQUESTED);
        reviewed.setReviewNote(reason);
        reviewed.setIsCurrent(false);
        versionRepository.save(reviewed);

        ScriptVersion next = versionRepository.save(ScriptVersion.builder()
            .versionNo(reviewed.getVersionNo() + 1)
            .title(reviewed.getTitle())
            .summary(reviewed.getSummary())
            .status(ScriptStatus.CHANGE_REQUESTED)
            .reviewNote(reason)
            .isCurrent(true)
            .project(project)
            .createdBy(authService.findUserOrThrow(reviewerId))
            .build());

        List<Scene> copied = copyScenes(scenesOf(reviewed), next);
        project.setScriptStatus(ScriptStatus.CHANGE_REQUESTED);
        projectRepository.save(project);
        statusHistoryService.recordReview(
            project, from, authService.findUserOrThrow(reviewerId),
            com.stemotion.domain.project.entity.StatusHistory.TargetType.SCRIPT, reviewed.getId(),
            com.stemotion.domain.project.entity.StatusHistory.Decision.REJECTED,
            reason);

        return toResponse(project, next, copied);
    }

    @Transactional
    public void delete(String projectId, String actorId) {
        ClipProject project = getOrThrow(projectId);
        if (Boolean.TRUE.equals(project.getArchived())) return;
        if (project.getVideoStatus() == VideoStatus.RENDERING) {
            throw com.stemotion.core.exception.ApiException.conflict("Cancel the running render before archiving this project");
        }
        var from = project.getPipelineStatus();
        project.setArchived(true);
        projectRepository.save(project);
        statusHistoryService.record(project, from, authService.findUserOrThrow(actorId), "Project archived");
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
            project.getCreatedAt(),
            visualThemeOf(version)
        );
    }

    private String visualThemeOf(ScriptVersion version) {
        if (compositionRepository == null || version.getId() == null) return "default";
        // Only the theme column, not the composition's scene JSON (DB performance plan 2.12).
        return compositionRepository.findVisualThemeByScriptVersionId(version.getId()).orElse("default");
    }

    // --------------------------------------------------------------- internals

    private List<Scene> replaceScenes(ScriptVersion version, List<SceneRequest> requests) {
        List<Scene> existing = sceneRepository.findForUpdateByScriptVersionId(version.getId());
        List<SceneRequest> incoming = requests == null ? List.of() : requests;
        java.util.Map<String, Scene> byId = new java.util.HashMap<>();
        for (Scene scene : existing) {
            byId.put(scene.getId(), scene);
            String legacyId = sceneMapper.legacyClientId(scene);
            if (legacyId != null && !legacyId.isBlank() && !byId.containsKey(legacyId)) byId.put(legacyId, scene);
        }
        java.util.Set<String> received = new java.util.HashSet<>();
        java.util.Set<String> retained = new java.util.HashSet<>();
        List<Scene> matches = new java.util.ArrayList<>();
        // Validate the full identity/deletion plan before mutating any managed row.
        for (int i = 0; i < incoming.size(); i++) {
            String id = incoming.get(i).getId();
            Scene match = null;
            if (id != null && !id.isBlank()) {
                if (!received.add(id)) throw ApiException.badRequest("Duplicate scene ID in draft");
                match = byId.get(id);
                if (match == null && id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                    throw ApiException.badRequest("Scene ID does not belong to the current script version; reload the draft");
                }
            } else if (!existing.isEmpty()) {
                // Compatibility for older id-less clients: in-place edits only.
                if (incoming.size() != existing.size()) throw ApiException.badRequest("Scene IDs are required when adding or removing scenes");
                match = existing.get(i);
            }
            if (match != null && !retained.add(match.getId())) throw ApiException.badRequest("Scene occurs more than once in draft");
            matches.add(match);
        }
        List<Scene> removed = existing.stream().filter(scene -> !retained.contains(scene.getId())).toList();
        for (Scene scene : removed) {
            if (sceneRepository.hasFeedback(scene.getId())) {
                throw ApiException.badRequest("Cannot delete a scene with feedback; review history must be preserved");
            }
        }
        List<Scene> scenes = new java.util.ArrayList<>(incoming.size());
        for (int i = 0; i < incoming.size(); i++) {
            Scene scene = matches.get(i);
            if (scene == null) scene = sceneMapper.toEntity(incoming.get(i), i + 1, version);
            else sceneMapper.updateEntity(scene, incoming.get(i), i + 1, version);
            scenes.add(scene);
        }
        sceneRepository.deleteAll(removed);
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

    /** Only the writer who owns the project may hand it to the reviewers. */
    private void requireOwner(ClipProject project, String userId) {
        if (!project.getOwner().getId().equals(userId)) {
            throw ApiException.badRequest("Only the project owner can submit this script for review");
        }
    }

    private void requireStatus(ClipProject project, ScriptStatus... allowed) {
        requireActive(project);
        for (ScriptStatus status : allowed) {
            if (project.getScriptStatus() == status) {
                return;
            }
        }
        throw ApiException.badRequest(
            "Script is " + project.getScriptStatus() + "; this transition is not allowed from that state"
        );
    }

    public void requireActive(ClipProject project) {
        if (Boolean.TRUE.equals(project.getArchived())) {
            throw ApiException.conflict("An archived project cannot be changed");
        }
    }

    private static ScriptSummaryResponse toSummary(com.stemotion.domain.project.repository.ProjectSummaryRow row) {
        return new ScriptSummaryResponse(
            row.id(),
            row.title(),
            row.subject(),
            row.gradeLevel(),
            row.totalDurationSeconds(),
            row.scriptStatus(),
            row.videoStatus(),
            row.ownerName(),
            row.sceneCount() == null ? 0 : row.sceneCount().intValue(),
            row.updatedAt()
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
