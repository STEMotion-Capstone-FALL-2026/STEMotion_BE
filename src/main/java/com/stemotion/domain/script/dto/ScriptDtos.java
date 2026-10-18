package com.stemotion.domain.script.dto;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class ScriptDtos {

    private ScriptDtos() {}

    /** Creates a clip project inside a workspace. */
    public record CreateScriptRequest(
        @NotBlank(message = "title is required")
        String title,

        @NotNull(message = "subject is required")
        StemSubject subject,

        String gradeLevel,

        Integer fps,

        @Valid
        List<SceneRequest> scenes
    ) {}

    /** Saves a draft: metadata plus the full ordered scene list. */
    public record UpdateScriptRequest(
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer fps,
        String summary,

        @Valid
        List<SceneRequest> scenes
    ) {}

    /** Mirrors the front end's STEMScript interface. */
    public record ScriptResponse(
        String id,
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer totalDurationSeconds,
        ScriptStatus scriptStatus,
        VideoStatus videoStatus,
        Integer fps,
        List<SceneResponse> scenes,
        String workspaceId,
        String ownerId,
        String ownerName,
        Integer currentVersionNo,
        String videoUrl,
        LocalDateTime createdAt,
        /** The production composition's visual theme; "default" before the producer picks one. */
        String visualTheme
    ) {}

    /** Lightweight row for dashboards and the pipeline board. */
    public record ScriptSummaryResponse(
        String id,
        String title,
        StemSubject subject,
        String gradeLevel,
        Integer totalDurationSeconds,
        ScriptStatus scriptStatus,
        VideoStatus videoStatus,
        String ownerName,
        Integer sceneCount,
        LocalDateTime updatedAt
    ) {}

    /** Reviewer decision payload for approve / request-changes. */
    public record ReviewDecisionRequest(
        String note
    ) {}

    public record ScriptVersionResponse(
        String id,
        Integer versionNo,
        String title,
        String summary,
        ScriptStatus status,
        String reviewNote,
        Boolean isCurrent,
        String createdById,
        String createdByName,
        LocalDateTime createdAt
    ) {}

    /** Side-by-side payload backing the version diff modal. */
    public record VersionDiffResponse(
        ScriptVersionResponse left,
        List<SceneResponse> leftScenes,
        ScriptVersionResponse right,
        List<SceneResponse> rightScenes
    ) {}
}
