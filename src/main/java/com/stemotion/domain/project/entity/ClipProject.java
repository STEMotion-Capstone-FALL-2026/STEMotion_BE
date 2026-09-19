package com.stemotion.domain.project.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.workspace.entity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A STEM clip project. Carries the two independent states of the pipeline:
 * {@code scriptStatus} for the editorial side and {@code videoStatus} for the
 * rendered clip, matching the STEMScript contract consumed by the front end.
 */
@Entity
@Table(name = "clip_projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClipProject extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject", nullable = false)
    private StemSubject subject;

    @Column(name = "grade_level")
    private String gradeLevel;

    @Column(name = "fps", nullable = false)
    @Builder.Default
    private Integer fps = 30;

    /** Sum of the scene durations of the current script version, in seconds. */
    @Column(name = "total_duration_seconds", nullable = false)
    @Builder.Default
    private Integer totalDurationSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_status", nullable = false)
    @Builder.Default
    private ScriptStatus scriptStatus = ScriptStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_status", nullable = false)
    @Builder.Default
    private VideoStatus videoStatus = VideoStatus.NOT_RENDERED;

    /** URL of the most recently rendered MP4, if any. */
    @Column(name = "video_url")
    private String videoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;
}
