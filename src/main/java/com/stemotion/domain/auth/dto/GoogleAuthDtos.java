package com.stemotion.domain.auth.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class GoogleAuthDtos {
    private GoogleAuthDtos() {}
    public record Credential(@NotBlank @Size(max=16000) String credential, @NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String challengeId,
                             @NotBlank @Size(max=128) String challengeSecret) {}
    public record Challenge(String challengeId, String nonce, String challengeSecret, int expiresInSeconds) {}
    public record LinkChallenge(@NotBlank @Size(max=256) String currentPassword) {}
    public record Rename(@NotBlank @Size(min=2,max=100) String fullName) {}
    public record WhitelistCreate(@NotBlank @Email String email) {}
    public record WhitelistUpdate(@NotNull @Pattern(regexp="ACTIVE|REVOKED") String status, @NotNull Long version) {}
    public record WhitelistRow(String id, String email, String status, long version, LocalDateTime createdAt, String boundUserId,
        com.stemotion.domain.notification.NotificationService.Summary notification) {}
    public record Profile(AuthDtos.UserResponse user, AuthDtos.SessionResponse session, boolean googleLinked,
                          String googleEmail, boolean googleLoginAllowed, boolean canLinkGoogle) {}
}
