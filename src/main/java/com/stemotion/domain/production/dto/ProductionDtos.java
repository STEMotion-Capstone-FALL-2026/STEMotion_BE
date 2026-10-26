package com.stemotion.domain.production.dto;

import com.stemotion.core.enums.StemEnums.PublishTarget;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.domain.production.entity.PublishRecord;
import com.stemotion.domain.production.entity.RemotionTemplate;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ProductionDtos {

    private ProductionDtos() {}

    // ----------------------------------------------------------------- render

    public record StartRenderRequest(
        String resolution,
        Integer fps,
        String ttsVoice
    ) {}

    public record RenderJobResponse(
        String jobId,
        String projectId,
        RenderStatus status,
        Integer progress,
        String stage,
        String videoUrl,
        String errorMessage,
        Integer estimatedSeconds,
        LocalDateTime createdAt,
        /** Script version the clip was rendered from, so re-renders can be compared. */
        Integer scriptVersionNo,
        /** Composition version rendered; null when rendered straight from the script. */
        Integer compositionRevision
    ) {
        public static RenderJobResponse from(RenderJob job, Integer estimatedSeconds) {
            return from(job, estimatedSeconds, null);
        }

        public static RenderJobResponse from(RenderJob job, Integer estimatedSeconds, Integer scriptVersionNo) {
            return new RenderJobResponse(
                job.getId(),
                job.getProject().getId(),
                job.getStatus(),
                job.getProgress(),
                job.getStage(),
                job.getVideoUrl(),
                job.getErrorMessage(),
                estimatedSeconds,
                job.getCreatedAt(),
                scriptVersionNo,
                job.getCompositionRevision()
            );
        }
    }

    // ------------------------------------------------------------ composition

    /**
     * The producer's draft of an approved script. {@code revision} 0 means
     * nothing has been saved yet and the scenes are the approved script's.
     */
    public record CompositionResponse(
        String projectId,
        Integer scriptVersionNo,
        Integer revision,
        boolean editable,
        /** Why the draft cannot change right now; null when editable. */
        String lockedReason,
        List<Map<String, Object>> scenes,
        LocalDateTime updatedAt,
        String updatedByName,
        String visualTheme
    ) {}

    public record UpdateCompositionRequest(
        /** The revision the edit started from; a different current revision is a conflict. */
        @NotNull(message = "baseRevision is required")
        @Min(value = 0, message = "baseRevision must not be negative")
        Integer baseRevision,

        @NotEmpty(message = "scenes are required")
        List<@Valid SceneRequest> scenes,

        /** default, cyber, emerald, sunset or academic; null keeps the current theme. */
        String visualTheme
    ) {
        public UpdateCompositionRequest(Integer baseRevision, List<SceneRequest> scenes) {
            this(baseRevision, scenes, null);
        }
    }

    /**
     * Measure scene durations from real TTS audio (BE-P0-2). For a production composition the
     * revision the studio shows is required, as for a save.
     */
    public record SyncDurationsRequest(Integer baseRevision) {}

    /**
     * target SCRIPT (a draft's scene rows were updated; revision is the new draftRevision) or
     * COMPOSITION (a new revision was saved);
     * scenes carry id, durationInFrames and, when Whisper ran, captionWords.
     */
    public record SyncDurationsResponse(
        String target,
        Integer revision,
        Integer totalDurationSeconds,
        List<Map<String, Object>> scenes,
        com.fasterxml.jackson.databind.JsonNode captionSync
    ) {}

    /** Progress pushed back by the Node render worker. */
    public record RenderCallbackRequest(
        @NotBlank(message = "jobId is required")
        String jobId,
        RenderStatus status,
        Integer progress,
        String stage,
        String videoUrl,
        String errorMessage,
        /** Caption/audio sync summary from Whisper alignment, sent with COMPLETED. */
        com.fasterxml.jackson.databind.JsonNode captionSync
    ) {
        public RenderCallbackRequest(String jobId, RenderStatus status, Integer progress, String stage,
                                     String videoUrl, String errorMessage) {
            this(jobId, status, progress, stage, videoUrl, errorMessage, null);
        }
    }

    // ---------------------------------------------------------------- publish

    public record PublishRequest(
        @NotEmpty(message = "at least one target is required")
        List<PublishTarget> targets,

        @NotBlank(message = "title is required")
        String title,

        String description
    ) {}

    public record PublishResponse(
        String id,
        String projectId,
        String lmsEmbedCode,
        String youtubeUrl,
        LocalDateTime publishedAt
    ) {
        public static PublishResponse from(PublishRecord record) {
            return new PublishResponse(
                record.getId(),
                record.getProject().getId(),
                record.getLmsEmbedCode(),
                record.getYoutubeUrl(),
                record.getCreatedAt()
            );
        }
    }

    // --------------------------------------------------------------- template

    public record TemplateRequest(
        @NotBlank(message = "name is required")
        String name,
        String category,
        String propsSchema,
        String previewUrl,
        Boolean isActive,
        String subject,
        String assetPack,
        String slots,
        /** StemSubject names (Math, Physics, Chemistry, Biology, ComputerScience); null keeps the current list. */
        List<String> recommendedSubjects,
        /** One of the visual themes; null keeps the current one, "" clears it. */
        String defaultVisualTheme
    ) {
        public TemplateRequest(String name, String category, String propsSchema, String previewUrl, Boolean isActive,
                               String subject, String assetPack, String slots) {
            this(name, category, propsSchema, previewUrl, isActive, subject, assetPack, slots, null, null);
        }
    }

    public record TemplateResponse(
        String id,
        String name,
        String category,
        String propsSchema,
        String previewUrl,
        Boolean isActive,
        String subject,
        String assetPack,
        String slots,
        List<String> recommendedSubjects,
        String defaultVisualTheme
    ) {
        private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

        private static List<String> subjects(String json) {
            try {
                return json == null ? List.of() : JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception ex) {
                return List.of();
            }
        }

        public static TemplateResponse from(RemotionTemplate template) {
            return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getCategory(),
                template.getPropsSchema(),
                template.getPreviewUrl(),
                template.getIsActive(),
                template.getSubject(),
                template.getAssetPack(),
                template.getSlots(),
                subjects(template.getRecommendedSubjects()),
                template.getDefaultVisualTheme()
            );
        }
    }
}
