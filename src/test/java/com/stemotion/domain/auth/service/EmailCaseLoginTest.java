package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.JwtService;
import com.stemotion.domain.auth.dto.AuthDtos.LoginRequest;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.integration.client.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailCaseLoginTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = new AuthService(users, encoder, mock(JwtService.class), mock(GoogleTokenVerifier.class));

    private User account(String email) {
        User u = User.builder().fullName("Writer").email(email).role(User.UserRole.WRITER)
            .status(User.UserStatus.ACTIVE).passwordHash(encoder.encode("test-password-only")).build();
        u.setId(email);
        return u;
    }

    @Test void anEmailTypedInAnotherCaseSignsInToTheOnlyMatchingAccount() {
        User stored = account("a@gmail.com");
        when(users.findByEmail(" A@gmail.com ".trim())).thenReturn(Optional.empty());
        when(users.findAllByEmailIgnoreCase("A@gmail.com")).thenReturn(List.of(stored));
        assertThat(auth.login(new LoginRequest(" A@gmail.com ", "test-password-only")).user().id()).isEqualTo("a@gmail.com");
    }

    @Test void twoAccountsDifferingOnlyInCaseNeedTheExactEmail() {
        when(users.findByEmail("B@gmail.com")).thenReturn(Optional.empty());
        when(users.findAllByEmailIgnoreCase("B@gmail.com")).thenReturn(List.of(account("b@gmail.com"), account("b@GMAIL.com")));
        assertThatThrownBy(() -> auth.login(new LoginRequest("B@gmail.com", "test-password-only")))
            .isInstanceOf(ApiException.class);
    }
}
