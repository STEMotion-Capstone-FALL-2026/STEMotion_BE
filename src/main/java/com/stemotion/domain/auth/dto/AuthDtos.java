package com.stemotion.domain.auth.dto;

import com.stemotion.domain.auth.entity.SystemRole;

import com.stemotion.domain.auth.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "fullName is required")
        @Size(min=2,max=100)
        String fullName,

        @NotBlank(message = "username is required")
        @Size(max=32)
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password,

        SystemRole systemRole
    ) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String field, Object value) { throw new IllegalArgumentException("Unknown account field"); }
    }

    public record LoginRequest(
        @NotBlank(message = "username is required")
        @Size(max=254)
        String username,

        @NotBlank(message = "password is required")
        @Size(max=256)
        String password
    ) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String field, Object value) { throw new IllegalArgumentException("Unknown account field"); }
    }

    public record GoogleLoginRequest(
        @NotBlank(message = "credential is required")
        String credential
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword is required")
        @Size(max=256)
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max=72, message = "newPassword must have 8 to 72 characters; UTF-8 byte limit also applies")
        String newPassword
    ) {}

    public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UserResponse user,
        SessionResponse session
    ) {}

    public record SessionResponse(String authMethod, boolean requiresPasswordChange) {}
    public record CurrentSession(UserResponse user, SessionResponse session) {}

    public record UserResponse(
        String id,
        String fullName,
        String email,
        SystemRole systemRole,
        User.UserStatus status,
        boolean mustChangePassword,
        LocalDateTime createdAt,
        String username,
        boolean hasLocalPassword,
        boolean googleLinked,
        String googleEmail,
        long version
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getSystemRole(),
                user.getStatus(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                user.getUsername(),
                user.getPasswordHash() != null,
                user.getGoogleIdentity() != null,
                user.getGoogleIdentity() == null ? null : user.getGoogleIdentity().getEmail(),
                user.getVersion()
            );
        }
    }

    public record UpdateUserRequest(
        String fullName,
        SystemRole systemRole,
        User.UserStatus status,

        /** A new temporary password; the user must change it at the next sign-in. */
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
    ) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String field, Object value) { throw new IllegalArgumentException("Unknown account field"); }
    }
}
