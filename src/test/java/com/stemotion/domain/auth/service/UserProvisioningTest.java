package com.stemotion.domain.auth.service;

import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class UserProvisioningTest {
    @Test void storesOnlyPasswordHashAndPreservesExistingUsers() {
        var repository = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder();
        var service = new UserService(repository, encoder);
        when(repository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertThat(user.getPasswordHash()).isNotEqualTo("test-password-only");
            assertThat(encoder.matches("test-password-only", user.getPasswordHash())).isTrue();
            user.setId("new-id"); return user;
        });
        var created = service.create(new RegisterRequest("Teacher", "teacher@example.invalid", "test-password-only", User.UserRole.ADMIN));
        assertThat(created.role()).isEqualTo(User.UserRole.ADMIN);
        verify(repository, never()).deleteById(any());
    }
    @Test void duplicateEmailDoesNotOverwriteExistingAccount() {
        var repository = mock(UserRepository.class);
        when(repository.existsByEmailIgnoreCase("existing@example.invalid")).thenReturn(true);
        var service = new UserService(repository, new BCryptPasswordEncoder());
        assertThatThrownBy(() -> service.create(new RegisterRequest("Teacher", "existing@example.invalid", "test-password-only", User.UserRole.WRITER)))
            .isInstanceOf(com.stemotion.core.exception.ApiException.class);
        verify(repository, never()).save(any());
    }
}
