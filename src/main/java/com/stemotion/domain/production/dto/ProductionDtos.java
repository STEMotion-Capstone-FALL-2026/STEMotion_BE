package com.stemotion.domain.production.dto;

import com.stemotion.core.enums.StemEnums.PublishTarget;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.domain.production.entity.PublishRecord;
import com.stemotion.domain.production.entity.RemotionTemplate;
import com.stemotion.domain.production.entity.RenderJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

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
        Integer scriptVersionNo
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
                scriptVersionNo
            );
        }
    }

    /** Progress pushed back by the Node render worker. */
    public record RenderCallbackRequest(
        @NotBlank(message = "jobId is required")
        String jobId,
        RenderStatus status,
        Integer progress,
        String stage,
        String videoUrl,
        String errorMessage
    ) {}

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
        String slots
    ) {}

    public record TemplateResponse(
        String id,
        String name,
        String category,
        String propsSchema,
        String previewUrl,
        Boolean isActive,
        String subject,
        String assetPack,
        String slots
    ) {
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
                template.getSlots()
            );
        }
    }
}
