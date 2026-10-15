package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.dto.AuthDtos.UpdateUserRequest;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.stemotion.domain.auth.dto.AuthDtos.UserResponse;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email is already registered");
        }
        User user = User.builder().fullName(request.fullName()).email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(request.role() == null ? User.UserRole.WRITER : request.role())
            .status(User.UserStatus.ACTIVE)
            .mustChangePassword(true)
            .build();
        // Return the account only, never a JWT belonging to the new user.
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(String id) {
        return UserResponse.from(getOrThrow(id));
    }

    @Transactional
    public UserResponse update(String id, UpdateUserRequest request) {
        User user = getOrThrow(id);
        if (request.fullName() != null) user.setFullName(request.fullName());
        if (request.role() != null) user.setRole(request.role());
        if (request.status() != null) user.setStatus(request.status());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setMustChangePassword(true);
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(String id) {
        if (!userRepository.existsById(id)) {
            throw ApiException.notFound("User", id);
        }
        userRepository.deleteById(id);
    }

    private User getOrThrow(String id) {
        return userRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("User", id));
    }
}
