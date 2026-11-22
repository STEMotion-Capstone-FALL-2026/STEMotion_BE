package com.stemotion.domain.collab.entity;

import com.stemotion.core.entity.BaseEntity;
import com.stemotion.domain.collab.entity.CollabEnums.LeaseResource;
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

/** One edit lease per script draft or composition of a project (FR-31, BR-31, D16/D17). Table 31. */
@Entity
@Table(name = "edit_leases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditLease extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 16, nullable = false)
    private LeaseResource resourceType;

    /** The project id: one script draft and one composition per project are edited at a time. */
    @Column(name = "resource_id", length = 36, nullable = false)
    private String resourceId;

    @Column(name = "holder_id", length = 36, nullable = false)
    private String holderId;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    @Column(name = "renewed_at", nullable = false)
    private LocalDateTime renewedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "released_by", length = 36)
    private String releasedBy;

    public boolean validAt(LocalDateTime now) {
        return releasedBy == null && expiresAt.isAfter(now);
    }
}
