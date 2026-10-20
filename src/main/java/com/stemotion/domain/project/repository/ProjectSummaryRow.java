package com.stemotion.domain.project.repository;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;

import java.time.LocalDateTime;

/** One row of a project list, read in a single query with its owner name and scene count. */
public record ProjectSummaryRow(
    String id,
    String title,
    StemSubject subject,
    String gradeLevel,
    Integer totalDurationSeconds,
    ScriptStatus scriptStatus,
    VideoStatus videoStatus,
    String ownerName,
    Long sceneCount,
    LocalDateTime updatedAt
) {}
