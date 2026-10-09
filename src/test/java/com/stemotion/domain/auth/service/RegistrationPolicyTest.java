package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.workspace.service.WorkspaceService;
import com.stemotion.core.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RegistrationPolicyTest {
    @Test
    void publicSignupCannotProvisionAdministratorOrTouchPersistence() {
        var users = mock(UserRepository.class);
        var workspace = mock(WorkspaceService.class);
        var service = new AuthService(users, mock(PasswordEncoder.class), mock(JwtService.class), workspace);
        assertThatThrownBy(() -> service.register(new RegisterRequest(
            "Test", "admin@example.invalid", "password123", User.UserRole.ADMIN)))
            .isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(403));
        verifyNoInteractions(users, workspace);
    }
}
