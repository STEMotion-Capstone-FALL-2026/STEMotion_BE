package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.core.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RegistrationPolicyTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(User.UserRole.class)
    void publicSignupCannotProvisionAnyRoleOrTouchPersistence(User.UserRole role) {
        var users = mock(UserRepository.class);
        var service = new AuthService(users, mock(PasswordEncoder.class), mock(JwtService.class));
        assertThatThrownBy(() -> service.register(new RegisterRequest(
            "Test", "admin@example.invalid", "password123", role)))
            .isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(403));
        verifyNoInteractions(users);
    }
}
