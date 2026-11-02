package com.stemotion.domain.auth.service;

import com.stemotion.domain.auth.entity.SystemRole;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.AuthSession;
import com.stemotion.domain.auth.dto.AuthDtos;
import com.stemotion.domain.auth.dto.GoogleAuthDtos.*;
import com.stemotion.domain.auth.entity.*;
import com.stemotion.domain.auth.repository.*;
import com.stemotion.integration.client.GoogleTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class GoogleAuthService {
    private final GoogleWhitelistRepository whitelist;
    private final GoogleIdentityRepository identities;
    private final GoogleChallengeRepository challenges;
    private final UserRepository users;
    private final GoogleTokenVerifier verifier;
    private final AuthService auth;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    @Value("${app.auth-v2.google-enabled:false}") private boolean googleEnabled;
    @Value("${app.auth-v2.enabled:false}") private boolean authEnabled;
    public boolean enabled() { return authEnabled && googleEnabled && verifier.isConfigured(); }
    private void requireEnabled() {
        if (!enabled()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_LOGIN_DISABLED", "Đăng nhập Google chưa được bật.");
    }
    public static String gmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@gmail\\.com$") || email.length() > 254)
            throw ApiException.badRequest("Vui lòng nhập địa chỉ @gmail.com hợp lệ.");
        return email;
    }
    @Transactional
    public Challenge loginChallenge() { requireEnabled(); return issue("LOGIN", null); }
    @Transactional
    public Challenge linkChallenge(String userId, String password) {
        requireEnabled();
        // Reauthentication takes a snapshot. Exchange rechecks the credential version
        // under the user lock; do not hold a user lock while challenge cleanup runs.
        User user = auth.findUserOrThrow(userId);
        auth.requireActive(user);
        if (!AuthSession.current().isPassword() || user.isMustChangePassword() || user.getPasswordHash() == null || !PasswordRules.canCompare(password)
                || !encoder.matches(password, user.getPasswordHash()))
            throw error(HttpStatus.BAD_REQUEST, "REAUTHENTICATION_FAILED", "Vui lòng xác nhận mật khẩu tài khoản đã hoàn tất đổi mật khẩu lần đầu.");
        if (identities.findByUserId(userId).isPresent()) throw conflict("GOOGLE_ALREADY_LINKED");
        return issue("LINK", user);
    }
    private Challenge issue(String purpose, User user) {
        // Lazy bounded cleanup; no additional scheduler or Redis requirement.
        challenges.deleteExpired(LocalDateTime.now().minusMinutes(1));
        String nonce = secret(), secret = secret();
        var c = new GoogleChallenge(); c.setPurpose(purpose); c.setNonceHash(hash(nonce)); c.setSecretHash(hash(secret));
        c.setUserId(user == null ? null : user.getId()); c.setAuthVersion(user == null ? null : user.getAuthVersion());
        c.setExpiresAt(LocalDateTime.now().plusMinutes(5)); challenges.saveAndFlush(c);
        return new Challenge(c.getId(), nonce, secret, 300);
    }
    @Transactional
    public AuthDtos.AuthResponse exchange(Credential request, String linkingUserId) {
        requireEnabled();
        var google = verifier.verify(request.credential());
        String email;
        try { email = gmail(google.email()); }
        catch (ApiException ex) { throw outside(); }
        // Lock the challenge before any mutation. Every mutation commits or rolls back together.
        var c = challenges.lockById(request.challengeId()).orElseThrow(() -> conflict("GOOGLE_CHALLENGE_INVALID"));
        if (c.getConsumedAt() != null || !c.getExpiresAt().isAfter(LocalDateTime.now())
                || !equal(c.getSecretHash(), hash(request.challengeSecret()))
                || google.nonce() == null || !equal(c.getNonceHash(), hash(google.nonce()))
                || !(linkingUserId == null ? "LOGIN" : "LINK").equals(c.getPurpose())
                || !Objects.equals(c.getUserId(), linkingUserId)) throw conflict("GOOGLE_CHALLENGE_INVALID");
        var w = whitelist.lockByEmail(email).filter(GoogleWhitelist::isActive).orElseThrow(GoogleAuthService::outside);
        GoogleIdentity identity = identities.findByWhitelistId(w.getId()).orElse(null);
        User user;
        if (linkingUserId != null) {
            user = users.lockById(linkingUserId).orElseThrow();
            auth.requireActive(user);
            if (!AuthSession.current().isPassword() || user.getPasswordHash() == null || user.isMustChangePassword()
                || !Objects.equals(c.getAuthVersion(), user.getAuthVersion())) throw conflict("GOOGLE_CHALLENGE_INVALID");
            if (identity != null && !identity.getUser().getId().equals(user.getId())) throw conflict("GOOGLE_IDENTITY_IN_USE");
            var existing = identities.findByUserId(user.getId());
            if (existing.isPresent()) {
                if (!existing.get().getSubject().equals(google.subject())) throw conflict("GOOGLE_ALREADY_LINKED");
                if (!existing.get().getWhitelist().getId().equals(w.getId())) throw conflict("GOOGLE_IDENTITY_CHANGED");
                identity = existing.get();
            }
        } else if (identity != null) {
            user = users.lockById(identity.getUser().getId()).orElseThrow();
            auth.requireActive(user);
        } else {
            // A subject cannot be claimed twice, even under a different email spelling.
            if (identities.existsBySubject(google.subject())) throw conflict("GOOGLE_IDENTITY_IN_USE");
            // Unreviewed legacy bindings must not silently produce a second user either.
            if (users.findByGoogleId(google.subject()).isPresent()) throw conflict("GOOGLE_LEGACY_REVIEW_REQUIRED");
            user = User.builder().fullName(initialName(google.name())).systemRole(SystemRole.USER).build();
            users.saveAndFlush(user);
        }
        if (identity != null && (!identity.getSubject().equals(google.subject()) || !identity.getEmail().equals(email)))
            throw conflict("GOOGLE_IDENTITY_CHANGED");
        if (identity == null) {
            if (identities.existsBySubject(google.subject())) throw conflict("GOOGLE_IDENTITY_IN_USE");
            var legacy = users.findByGoogleId(google.subject());
            if (legacy.isPresent() && !legacy.get().getId().equals(user.getId())) throw conflict("GOOGLE_IDENTITY_IN_USE");
            identity = new GoogleIdentity(); identity.setUser(user); identity.setWhitelist(w);
            identity.setEmail(email); identity.setSubject(google.subject()); identity.setLinkedAt(LocalDateTime.now());
        }
        identity.setLastLoginAt(LocalDateTime.now()); identities.saveAndFlush(identity);
        user.setGoogleIdentity(identity);
        c.setConsumedAt(LocalDateTime.now()); challenges.save(c);
        if (linkingUserId != null) { user.setAuthVersion(user.getAuthVersion() + 1); users.saveAndFlush(user); }
        log.info("auth_google action={} userId={} whitelistId={}", linkingUserId == null ? "LOGIN" : "LINK", user.getId(), w.getId());
        return auth.response(user, linkingUserId == null ? "GOOGLE" : "PASSWORD", linkingUserId == null ? identity.getId() : null);
    }
    @Transactional(readOnly=true)
    public Profile profile(String userId) {
        var user = auth.findUserOrThrow(userId); var identity = identities.findByUserId(userId);
        return new Profile(AuthDtos.UserResponse.from(user), auth.session(user, AuthSession.current().method()),
            identity.isPresent(), identity.map(GoogleIdentity::getEmail).orElse(null),
            identity.map(i -> i.getWhitelist().isActive()).orElse(false),
            enabled() && user.getPasswordHash() != null && !user.isMustChangePassword() && identity.isEmpty() && AuthSession.current().isPassword());
    }
    @Transactional
    public Profile rename(String userId, String rawName) {
        String name = AccountNames.fullName(rawName);
        var user = users.lockById(userId).orElseThrow(); auth.requireActive(user);
        user.setFullName(name); users.saveAndFlush(user);
        log.info("auth_profile action=RENAME userId={}", userId);
        return profile(userId);
    }
    private String secret() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static boolean equal(String a, String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
    private static String initialName(String name) {
        if (name == null || name.trim().length() < 2 || name.length() > 100 || name.chars().anyMatch(Character::isISOControl)) return "Người dùng STEMotion";
        return name.trim();
    }
    private static ApiException outside() { return error(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_IN_SYSTEM", "Tài khoản không nằm trong hệ thống."); }
    private static ApiException conflict(String code) {
        String message = switch (code) {
            case "GOOGLE_IDENTITY_IN_USE" -> "Gmail này đã liên kết với tài khoản khác trong hệ thống.";
            case "GOOGLE_ALREADY_LINKED" -> "Tài khoản đã liên kết với một danh tính Google khác.";
            case "GOOGLE_IDENTITY_CHANGED" -> "Danh tính Google không khớp với liên kết đã lưu.";
            case "GOOGLE_LEGACY_REVIEW_REQUIRED" -> "Liên kết Google cũ chưa được chuyển đổi. Vui lòng dùng tài khoản và mật khẩu hiện có.";
            default -> "Phiên xác nhận Google không hợp lệ hoặc đã hết hạn. Vui lòng bắt đầu lại.";
        };
        return error(HttpStatus.CONFLICT, code, message);
    }
    private static ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
