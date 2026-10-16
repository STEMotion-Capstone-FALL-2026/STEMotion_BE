package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.JwtService;
import com.stemotion.domain.auth.dto.AuthDtos.AuthResponse;
import com.stemotion.domain.auth.dto.AuthDtos.LoginRequest;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import com.stemotion.domain.auth.dto.AuthDtos.UserResponse;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.stemotion.integration.client.GoogleTokenVerifier googleTokenVerifier;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        throw ApiException.forbidden("Public registration is disabled. Contact an administrator for an account");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(AuthService::invalidCredentials);

        if (user.getPasswordHash() == null
            || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw ApiException.badRequest("Account is " + user.getStatus().name().toLowerCase());
        }

        return buildAuthResponse(user);
    }

    /**
     * Google Sign-In for accounts an admin has already provisioned: the verified
     * Google email must match an existing account. Nobody can create an account
     * by signing in with Google. The first sign-in links the Google id; later
     * ones must come from the same Google account.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        var identity = googleTokenVerifier.verify(idToken);
        User user = userRepository.findByEmail(identity.email())
            .orElseThrow(() -> ApiException.forbidden(
                "Email này chưa được quản trị viên cấp tài khoản STEMotion."));
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw ApiException.badRequest("Account is " + user.getStatus().name().toLowerCase());
        }
        if (user.getGoogleId() == null) {
            user.setGoogleId(identity.subject());
            userRepository.save(user);
        } else if (!user.getGoogleId().equals(identity.subject())) {
            throw ApiException.forbidden("Tài khoản này đã liên kết với một tài khoản Google khác.");
        }
        return buildAuthResponse(user);
    }

    public boolean isGoogleLoginEnabled() {
        return googleTokenVerifier.isConfigured();
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        return UserResponse.from(findUserOrThrow(userId));
    }

    @Transactional
    public UserResponse changePassword(String userId, com.stemotion.domain.auth.dto.AuthDtos.ChangePasswordRequest request) {
        User user = findUserOrThrow(userId);
        if (user.getPasswordHash() == null
            || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password does not match");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("User", userId));
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
            jwtService.generateToken(user),
            "Bearer",
            jwtService.getExpirationMs(),
            UserResponse.from(user)
        );
    }

    private static ApiException invalidCredentials() {
        return ApiException.badRequest("Invalid email or password");
    }
}
