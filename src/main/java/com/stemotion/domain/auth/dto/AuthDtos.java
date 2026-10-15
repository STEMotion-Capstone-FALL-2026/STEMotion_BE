package com.stemotion.domain.auth.dto;

import com.stemotion.domain.auth.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "fullName is required")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password,

        User.UserRole role
    ) {}

    public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotBlank(message = "password is required")
        String password
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, message = "newPassword must be at least 8 characters")
        String newPassword
    ) {}

    public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UserResponse user
    ) {}

    public record UserResponse(
        String id,
        String fullName,
        String email,
        User.UserRole role,
        User.UserStatus status,
        boolean mustChangePassword,
        LocalDateTime createdAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.isMustChangePassword(),
                user.getCreatedAt()
            );
        }
    }

    public record UpdateUserRequest(
        String fullName,
        User.UserRole role,
        User.UserStatus status,
        String password
    ) {}
}
