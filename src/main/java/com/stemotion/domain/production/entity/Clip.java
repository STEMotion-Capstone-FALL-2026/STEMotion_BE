package com.stemotion.domain.production.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A completed render artifact. Human review is tracked separately in history. */
@Entity
@Table(name = "clips")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Clip extends BaseEntity {
    public enum QaStatus { PENDING, PASS, FAIL }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "render_job_id", nullable = false, unique = true)
    private RenderJob renderJob;

    @Column(name = "video_url", nullable = false, columnDefinition = "text")
    private String videoUrl;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "qa_status", nullable = false, length = 16)
    @Builder.Default
    private QaStatus qaStatus = QaStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "qa_report", columnDefinition = "jsonb")
    private String qaReport;
}
