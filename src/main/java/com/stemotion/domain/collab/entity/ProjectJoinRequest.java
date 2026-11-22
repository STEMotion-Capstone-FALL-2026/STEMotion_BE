package com.stemotion.domain.collab.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.collab.entity.CollabEnums.JoinRequestStatus;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
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

/** A workspace member asking to join a project with a role (UC-30, D15). Table 30. */
@Entity
@Table(name = "project_join_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectJoinRequest extends BaseEntity {
    @Column(name = "project_id", length = 36, nullable = false)
    private String projectId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private WorkspaceRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    @Builder.Default
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "decided_by", length = 36)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
