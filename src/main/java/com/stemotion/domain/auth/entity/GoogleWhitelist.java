package com.stemotion.domain.auth.entity;
import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "google_login_whitelist") @Getter @Setter @NoArgsConstructor
public class GoogleWhitelist extends BaseEntity {
    @Column(nullable=false, unique=true) private String email;
    @Column(nullable=false) private String status = "ACTIVE";
    @Column(name="created_by", nullable=false) private String createdBy;
    @Column(name="updated_by", nullable=false) private String updatedBy;
    @Column(name="revoked_at") private LocalDateTime revokedAt;
    @Version private long version;
    @Column(name="grant_revision", nullable=false) private long grantRevision = 1;
    @OneToOne(mappedBy="whitelist", fetch=FetchType.LAZY) private GoogleIdentity identity;
    public boolean isActive() { return "ACTIVE".equals(status); }
}
