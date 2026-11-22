package com.stemotion.domain.collab.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.collab.entity.CollabEnums.ContributionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Append-only record of who did what in a project, for credit and traceability (FR-46, D18). Table 32. */
@Entity
@Table(name = "contribution_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionEvent extends BaseEntity {
    @Column(name = "project_id", length = 36, nullable = false, updatable = false)
    private String projectId;

    @Column(name = "actor_id", length = 36, nullable = false, updatable = false)
    private String actorId;

    /** WRITER, REVIEWER, PRODUCER or ADMIN, as held at the moment of the action. */
    @Column(name = "project_role", length = 16, nullable = false, updatable = false)
    private String projectRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 32, nullable = false, updatable = false)
    private ContributionType eventType;

    @Column(name = "target_id", length = 36, updatable = false)
    private String targetId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;
}
