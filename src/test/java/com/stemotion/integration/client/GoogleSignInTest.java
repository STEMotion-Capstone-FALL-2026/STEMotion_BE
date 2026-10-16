package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.JwtService;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Google Sign-In (proposal: JWT sessions and Google OAuth 2.0) only opens
 * accounts an admin has provisioned; it never creates one.
 */
class GoogleSignInTest {

    private static final String CLIENT = "client-123.apps.googleusercontent.com";
    private final ObjectMapper json = new ObjectMapper();

    private com.fasterxml.jackson.databind.JsonNode claims(String aud, String iss, long exp, String verified) throws Exception {
        return json.readTree("{\"aud\":\"" + aud + "\",\"iss\":\"" + iss + "\",\"exp\":\"" + exp
            + "\",\"email\":\"Teacher@School.edu.vn\",\"email_verified\":\"" + verified + "\",\"sub\":\"google-sub-1\"}");
    }

    @Test
    @DisplayName("a valid Google token yields the verified, lower-cased email")
    void acceptsValidClaims() throws Exception {
        Instant now = Instant.parse("2026-10-16T00:00:00Z");
        var identity = GoogleTokenVerifier.check(claims(CLIENT, "accounts.google.com", now.getEpochSecond() + 60, "true"), CLIENT, now);
        assertThat(identity.email()).isEqualTo("teacher@school.edu.vn");
        assertThat(identity.subject()).isEqualTo("google-sub-1");
    }

    @Test
    @DisplayName("tokens for another app, from another issuer, expired or with an unverified email are refused")
    void refusesBadClaims() throws Exception {
        Instant now = Instant.parse("2026-10-16T00:00:00Z");
        long later = now.getEpochSecond() + 60;
        assertThatThrownBy(() -> GoogleTokenVerifier.check(claims("other-app", "accounts.google.com", later, "true"), CLIENT, now))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> GoogleTokenVerifier.check(claims(CLIENT, "evil.example.com", later, "true"), CLIENT, now))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> GoogleTokenVerifier.check(claims(CLIENT, "accounts.google.com", now.getEpochSecond() - 1, "true"), CLIENT, now))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> GoogleTokenVerifier.check(claims(CLIENT, "accounts.google.com", later, "false"), CLIENT, now))
            .isInstanceOf(ApiException.class);
    }

    private final UserRepository users = mock(UserRepository.class);
    private final GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
    private final AuthService auth = new AuthService(users, mock(PasswordEncoder.class), mock(JwtService.class), verifier);

    private User account(String googleId, User.UserStatus status) {
        User u = User.builder().fullName("Cô Lan").email("teacher@school.edu.vn").role(User.UserRole.WRITER)
            .status(status).googleId(googleId).build();
        u.setId("u1");
        return u;
    }

    @Test
    @DisplayName("an email no admin provisioned cannot sign in, and no account is created")
    void unknownEmailIsRefused() {
        when(verifier.verify("t")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("google-sub-1", "teacher@school.edu.vn"));
        when(users.findByEmail("teacher@school.edu.vn")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.loginWithGoogle("t")).isInstanceOf(ApiException.class).hasMessageContaining("chưa được");
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("the first Google sign-in links the account; a different Google account is refused later")
    void linksThenPinsTheGoogleAccount() {
        User user = account(null, User.UserStatus.ACTIVE);
        when(users.findByEmail("teacher@school.edu.vn")).thenReturn(Optional.of(user));
        when(verifier.verify("t")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("google-sub-1", "teacher@school.edu.vn"));
        auth.loginWithGoogle("t");
        assertThat(user.getGoogleId()).isEqualTo("google-sub-1");

        when(verifier.verify("other")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("google-sub-2", "teacher@school.edu.vn"));
        assertThatThrownBy(() -> auth.loginWithGoogle("other")).isInstanceOf(ApiException.class).hasMessageContaining("khác");
    }

    @Test
    @DisplayName("a suspended account cannot sign in with Google either")
    void inactiveAccountIsRefused() {
        when(users.findByEmail("teacher@school.edu.vn")).thenReturn(Optional.of(account("google-sub-1", User.UserStatus.INACTIVE)));
        when(verifier.verify("t")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("google-sub-1", "teacher@school.edu.vn"));
        assertThatThrownBy(() -> auth.loginWithGoogle("t")).isInstanceOf(ApiException.class);
    }
}
