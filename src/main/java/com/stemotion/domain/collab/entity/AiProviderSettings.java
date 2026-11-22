package com.stemotion.domain.collab.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Admin AI/TTS settings: allowed providers, models, voices and budgets (FR-44, D04/D07). Table 33. */
@Entity
@Table(name = "ai_provider_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiProviderSettings extends BaseEntity {
    /** Defaults agreed by the team on 22/11/2026. */
    public static final BigDecimal DEFAULT_MONTHLY = new BigDecimal("20.00");
    public static final BigDecimal DEFAULT_PER_CLIP = new BigDecimal("0.50");

    @Column(name = "allowed_ai_providers", columnDefinition = "text", nullable = false)
    @Builder.Default
    private String allowedAiProviders = "openai";

    @Column(name = "allowed_models", columnDefinition = "text", nullable = false)
    @Builder.Default
    private String allowedModels = "";

    @Column(name = "allowed_voices", columnDefinition = "text", nullable = false)
    @Builder.Default
    private String allowedVoices = "";

    @Column(name = "monthly_budget_usd", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal monthlyBudgetUsd = DEFAULT_MONTHLY;

    @Column(name = "per_clip_cap_usd", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal perClipCapUsd = DEFAULT_PER_CLIP;

    @Column(name = "revision", nullable = false)
    @Builder.Default
    private int revision = 0;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;
}
