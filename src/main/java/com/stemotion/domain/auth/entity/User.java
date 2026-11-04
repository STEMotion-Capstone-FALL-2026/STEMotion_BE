package com.stemotion.domain.auth.entity;

import com.stemotion.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    // The shared Supabase schema requires both name columns.
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @PrePersist
    @PreUpdate
    private void synchronizeDisplayName() {
        displayName = fullName;
    }

    /** Legacy contact address, never a password-login identifier. */
    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "username", unique = true, length = 254)
    private String username;

    @Column(name = "auth_version", nullable = false)
    @Builder.Default
    private long authVersion = 0;

    /** Explicit authentication/profile projection; never trigger identity queries on content authors. */
    @jakarta.persistence.Transient
    private GoogleIdentity googleIdentity;

    @Column(name = "google_id", unique = true)
    private String googleId;

    // Null for accounts created through Google sign-in
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false)
    @Builder.Default
    private SystemRole systemRole = SystemRole.USER;

    @jakarta.persistence.Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;


    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        BANNED
    }
}
