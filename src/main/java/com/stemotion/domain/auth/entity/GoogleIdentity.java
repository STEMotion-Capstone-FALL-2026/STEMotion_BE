package com.stemotion.domain.auth.entity;
import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="google_identities") @Getter @Setter @NoArgsConstructor
public class GoogleIdentity extends BaseEntity {
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id", nullable=false, unique=true)
    private User user;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="whitelist_id", nullable=false, unique=true)
    private GoogleWhitelist whitelist;
    @Column(name="google_subject", nullable=false, unique=true) private String subject;
    @Column(nullable=false, unique=true) private String email;
    @Column(name="linked_at", nullable=false) private LocalDateTime linkedAt;
    @Column(name="last_login_at") private LocalDateTime lastLoginAt;
}
