package com.stemotion.domain.collab.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.collab.entity.CollabEnums.MemberStatus;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
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

/** A member's role in one project, fixed when approved (FR-45, BR-32, D08/D09). Table 29. */
@Entity
@Table(name = "project_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMember extends BaseEntity {
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
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "revoked_by", length = 36)
    private String revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public boolean active() {
        return status == MemberStatus.ACTIVE;
    }
}
