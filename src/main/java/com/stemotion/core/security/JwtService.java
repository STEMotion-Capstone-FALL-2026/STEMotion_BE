package com.stemotion.core.security;

import com.stemotion.domain.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        return generateToken(user, "PASSWORD", null);
    }

    public String generateToken(User user, String method, String googleIdentityId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(user.getId())
            .claim("authMethod", method)
            .claim("authVersion", user.getAuthVersion())
            .claim("googleIdentityId", googleIdentityId)
            .claim("systemRole", user.getSystemRole().name())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMs))
            .signWith(signingKey)
            .compact();
    }

    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public AuthSession session(String token, User user) {
        Claims claims = parseClaims(token);
        String method = claims.get("authMethod", String.class);
        Number version = claims.get("authVersion", Number.class);
        if ((!"PASSWORD".equals(method) && !"GOOGLE".equals(method)) || version == null
                || version.longValue() != user.getAuthVersion()
                || ("PASSWORD".equals(method) && user.getPasswordHash() == null))
            throw new IllegalArgumentException("Expired authentication contract or credential");
        return new AuthSession(method, claims.get("googleIdentityId", String.class));
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
