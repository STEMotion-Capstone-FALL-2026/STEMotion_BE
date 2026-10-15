package com.stemotion.domain.review.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.entity.Scene;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A review note. Covers both review passes described in the proposal:
 * inline per-scene comments on the script, and timestamped pins on the
 * rendered clip (where {@code timestampSec} points into the video).
 */
@Entity
@Table(name = "feedback_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackComment extends BaseEntity {

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "script_version_id", columnDefinition = "uuid")
    private String scriptVersionId;

    @Column(name = "scene_key")
    private String sceneKey;

    @Column(name = "timestamp_val")
    private Double timestampValue;

    @PrePersist
    @PreUpdate
    void synchronizeTarget() {
        targetType = scene == null ? "CLIP_TIMESTAMP" : "SCRIPT_INLINE";
        scriptVersionId = scene == null ? null : scene.getScriptVersion().getId();
        sceneKey = scene == null ? null : scene.getId();
        timestampValue = timestampSec;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ClipProject project;

    /** Set for an inline script comment; null for a clip-level pin. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id")
    private Scene scene;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Position in the rendered clip, in seconds (e.g. 15.5). */
    @Column(name = "timestamp_sec")
    private Double timestampSec;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Free-form label used by the reviewer UI, e.g. "Đồ họa". */
    @Column(name = "tag")
    private String tag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FeedbackStatus status = FeedbackStatus.OPEN;
}
