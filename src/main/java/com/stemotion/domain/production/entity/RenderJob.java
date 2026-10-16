package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.RenderResolution;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.entity.ScriptVersion;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tracks one Remotion render handled by the external Node worker pool.
 *
 * <p>This row is the API's own record of the job; {@code externalJobId} is the
 * BullMQ id returned by the render service, which owns the actual execution.
 */
@Entity
@Table(name = "render_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenderJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private ScriptVersion scriptVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "external_job_id")
    private String externalJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RenderStatus status = RenderStatus.QUEUED;

    @Column(name = "progress", nullable = false)
    @Builder.Default
    private Integer progress = 0;

    @Column(name = "stage")
    private String stage;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", nullable = false)
    @Builder.Default
    private RenderResolution resolution = RenderResolution.RES_1080P;

    @Column(name = "fps", nullable = false)
    @Builder.Default
    private Integer fps = 30;

    @Column(name = "tts_voice")
    private String ttsVoice;

    /** Retry counter; the proposal allows three attempts before alerting. */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** The composition that triggered this render (ERD); null when rendered straight from the script. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composition_id")
    private Composition composition;

    /** Composition version rendered; null when rendered straight from the approved script. */
    @Column(name = "composition_revision")
    private Integer compositionRevision;

    /** Exact props sent to the worker, so a retry renders the same composition version. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_props", columnDefinition = "jsonb")
    private String inputProps;
}
