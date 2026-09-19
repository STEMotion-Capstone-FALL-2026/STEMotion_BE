package com.stemotion.domain.script.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "narration_text", columnDefinition = "TEXT")
    private String narrationText;

    @Column(name = "on_screen_text", columnDefinition = "TEXT")
    private String onScreenText;

    @Column(name = "suggested_duration")
    private Integer suggestedDuration;

    @Column(name = "template_type")
    private String templateType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private ScriptVersion scriptVersion;
}
