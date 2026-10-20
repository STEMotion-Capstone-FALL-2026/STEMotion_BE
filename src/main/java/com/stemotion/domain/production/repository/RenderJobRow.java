package com.stemotion.domain.production.repository;

import com.stemotion.core.enums.StemEnums.RenderStatus;

import java.time.LocalDateTime;

/** A render job as the history list shows it, without its input props JSON. */
public record RenderJobRow(
    String id,
    String projectId,
    RenderStatus status,
    Integer progress,
    String stage,
    String videoUrl,
    String errorMessage,
    LocalDateTime createdAt,
    Integer scriptVersionNo,
    Integer compositionRevision
) {}
