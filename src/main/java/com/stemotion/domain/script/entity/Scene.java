package com.stemotion.domain.script.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.SceneType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One scene of a script version, mapped to a Remotion template.
 *
 * <p>The seven scene types each carry different template props (latex steps,
 * chart data points, code walk-through steps, quiz options...). Rather than a
 * column per variant, the type-specific half of the payload is stored as JSONB
 * in {@code props} and validated against the scene type in the service layer.
 */
@Entity
@Table(name = "scenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scene extends BaseEntity {

    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    /** Transitional Supabase column; kept equal to the API's canonical order. */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @PrePersist
    @PreUpdate
    void synchronizeOrder() {
        orderIndex = orderNo;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "scene_type", nullable = false)
    private SceneType sceneType;

    @Column(name = "title", nullable = false)
    private String title;

    /** Narration read by the TTS voice. */
    @Column(name = "narration", columnDefinition = "TEXT")
    private String narration;

    /** Scene length in frames; seconds = durationInFrames / project fps. */
    @Column(name = "duration_in_frames", nullable = false)
    @Builder.Default
    private Integer durationInFrames = 90;

    /** Template-specific props as JSON, shaped by {@link #sceneType}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "props", columnDefinition = "jsonb")
    private String props;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private ScriptVersion scriptVersion;
}
