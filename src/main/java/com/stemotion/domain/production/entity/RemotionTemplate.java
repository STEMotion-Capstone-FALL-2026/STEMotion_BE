package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "remotion_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemotionTemplate extends BaseEntity {

    @Column(name = "subject")
    private String subject;

    @Column(name = "asset_pack")
    private String assetPack;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "slots", columnDefinition = "jsonb")
    private String slots;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "props_schema", columnDefinition = "TEXT")
    private String propsSchema;

    @Column(name = "preview_url")
    private String previewUrl;

    /** StemSubject names the template is recommended for, as a JSON array. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "recommended_subjects", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String recommendedSubjects = "[]";

    /** Visual theme suggested when a producer starts from this template; null for none. */
    @Column(name = "default_visual_theme", length = 32)
    private String defaultVisualTheme;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
