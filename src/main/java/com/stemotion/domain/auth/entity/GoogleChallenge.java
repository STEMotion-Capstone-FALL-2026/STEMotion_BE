package com.stemotion.domain.auth.entity;
import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="google_auth_challenges") @Getter @Setter @NoArgsConstructor
public class GoogleChallenge extends BaseEntity {
    @Column(name="nonce_hash", nullable=false) private String nonceHash;
    @Column(name="secret_hash", nullable=false) private String secretHash;
    @Column(nullable=false) private String purpose;
    @Column(name="user_id") private String userId;
    @Column(name="auth_version") private Long authVersion;
    @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
    @Column(name="consumed_at") private LocalDateTime consumedAt;
}
