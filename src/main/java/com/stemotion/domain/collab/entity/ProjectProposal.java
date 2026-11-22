package com.stemotion.domain.collab.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.domain.collab.entity.CollabEnums.ProposalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A Writer's proposal; a Leader approves it exactly once (FR-17, BR-30). Table 28. */
@Entity
@Table(name = "project_proposals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectProposal extends BaseEntity {
    @Column(name = "workspace_id", length = 36, nullable = false)
    private String workspaceId;

    @Column(name = "proposer_id", length = 36, nullable = false)
    private String proposerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject", length = 32, nullable = false)
    private StemSubject subject;

    @Column(name = "grade_level", length = 32)
    private String gradeLevel;

    @Column(name = "requested_roles", length = 128, nullable = false)
    @Builder.Default
    private String requestedRoles = "WRITER,REVIEWER,PRODUCER";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    @Builder.Default
    private ProposalStatus status = ProposalStatus.PENDING;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @Column(name = "decided_by", length = 36)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "project_id", length = 36)
    private String projectId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
