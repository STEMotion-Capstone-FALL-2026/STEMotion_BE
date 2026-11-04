package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.AuthSession;
import com.stemotion.core.security.JwtService;
import com.stemotion.domain.auth.dto.AuthDtos.*;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.integration.client.GoogleTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;
    @org.springframework.beans.factory.annotation.Autowired private com.stemotion.core.security.AuthRateLimiter rateLimiter;
    private static final String DUMMY_HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        .encode(java.util.UUID.randomUUID().toString());

    @Transactional(readOnly = true)
    public AuthResponse register(RegisterRequest request) {
        throw ApiException.forbidden("Public registration is disabled. Contact an administrator for an account");
    }
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = AccountNames.loginUsername(request.username());
        if (rateLimiter != null) rateLimiter.check("username:" + username);
        User user = userRepository.findByUsername(username).orElse(null);
        boolean comparable = PasswordRules.canCompare(request.password());
        boolean matches = passwordEncoder.matches(comparable ? request.password() : "invalid-long-password",
            user == null || user.getPasswordHash() == null ? DUMMY_HASH : user.getPasswordHash());
        if (user == null || user.getPasswordHash() == null || !matches || !comparable)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Tài khoản hoặc mật khẩu không đúng.");
        requireActive(user);
        return response(user, "PASSWORD", null);
    }
    /** The implicit email-linking entry is no longer a login mechanism. */
    @Transactional(readOnly = true)
    public AuthResponse loginWithGoogle(String token) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "GOOGLE_CHALLENGE_REQUIRED", "Vui lòng bắt đầu lại đăng nhập Google.");
    }
    public boolean isGoogleLoginEnabled() { return googleTokenVerifier.isConfigured(); }
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) { return UserResponse.from(findUserOrThrow(userId)); }
    @Transactional(readOnly = true)
    public CurrentSession currentSession(String userId) {
        User user = findUserOrThrow(userId);
        return new CurrentSession(UserResponse.from(user), session(user, AuthSession.current().method()));
    }
    @Transactional
    public AuthResponse changePassword(String userId, ChangePasswordRequest request) {
        PasswordRules.requireNew(request.newPassword());
        User user = userRepository.lockById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
        if (user.getPasswordHash() == null || !PasswordRules.canCompare(request.currentPassword())
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash()))
            throw ApiException.badRequest("Current password does not match");
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash()))
            throw ApiException.badRequest("New password must be different from the current password");
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setAuthVersion(user.getAuthVersion() + 1);
        userRepository.saveAndFlush(user);
        userRepository.hydrateProfiles(java.util.List.of(user));
        return response(user, AuthSession.current().method(), AuthSession.current().googleIdentityId());
    }
    @Transactional(readOnly = true)
    public User findUserOrThrow(String userId) {
        return userRepository.findForAuthentication(userId).orElseThrow(() -> ApiException.notFound("User", userId));
    }
    public void requireActive(User user) {
        if (user.getStatus() != User.UserStatus.ACTIVE)
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản đã bị khóa.");
    }
    public SessionResponse session(User user, String method) {
        return new SessionResponse(method, "PASSWORD".equals(method) && user.getPasswordHash() != null && user.isMustChangePassword());
    }
    public AuthResponse response(User user, String method, String googleIdentityId) {
        return new AuthResponse(jwtService.generateToken(user, method, googleIdentityId), "Bearer",
            jwtService.getExpirationMs(), UserResponse.from(user), session(user, method));
    }
}
