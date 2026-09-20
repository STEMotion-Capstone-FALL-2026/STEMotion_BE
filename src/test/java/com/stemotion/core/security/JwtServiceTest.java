package com.stemotion.core.security;

import com.stemotion.domain.auth.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "stemotion-test-secret-key-that-is-long-enough-for-hmac-sha";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    private static User user() {
        User user = User.builder()
            .fullName("Nguyen Van A")
            .email("writer@stemotion.vn")
            .role(User.UserRole.WRITER)
            .build();
        user.setId("user-123");
        return user;
    }

    @Test
    @DisplayName("a generated token carries the user id as its subject")
    void roundTripsUserId() {
        String token = jwtService.generateToken(user());

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo("user-123");
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(user());
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void rejectsForeignSignature() {
        JwtService other = new JwtService("a-completely-different-secret-key-of-sufficient-length", 60_000);
        String foreign = other.generateToken(user());

        assertThat(jwtService.isValid(foreign)).isFalse();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpiredToken() {
        JwtService expiring = new JwtService(SECRET, -1_000);
        String token = expiring.generateToken(user());

        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("garbage is rejected without throwing")
    void rejectsGarbage() {
        assertThat(jwtService.isValid("not-a-jwt")).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
    }
}
