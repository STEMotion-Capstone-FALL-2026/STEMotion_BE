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

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        return UserResponse.from(findUserOrThrow(userId));
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
