package com.stemotion.domain.auth.service;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.JwtService;
import com.stemotion.domain.auth.dto.AuthDtos.LoginRequest;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.integration.client.GoogleTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsernameLoginTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = new AuthService(users,encoder,mock(JwtService.class),mock(GoogleTokenVerifier.class));
    @Test void usernameIsCanonicalAndEmailIsNotQueried() {
        var u=User.builder().username("writer.one").fullName("Writer").passwordHash(encoder.encode("correct-password")).build();u.setId("u1");
        when(users.findByUsername("writer.one")).thenReturn(Optional.of(u));
        var r=auth.login(new LoginRequest(" Writer.One ","correct-password"));
        assertThat(r.user().id()).isEqualTo("u1");
        assertThat(r.session().authMethod()).isEqualTo("PASSWORD");
        verify(users,never()).findByEmail(anyString());
    }
    @Test void migratedEmailShapedUsernameUsesOnlyUsernameLookup() {
        var u=User.builder().username("a@gmail.com").fullName("Legacy").passwordHash(encoder.encode("correct-password")).build();u.setId("legacy");
        when(users.findByUsername("a@gmail.com")).thenReturn(Optional.of(u));
        assertThat(auth.login(new LoginRequest(" A@GMAIL.COM ","correct-password")).user().id()).isEqualTo("legacy");
        verify(users,never()).findByEmail(anyString());
        assertThatThrownBy(()->AccountNames.username("a@gmail.com")).isInstanceOf(ApiException.class);
    }
    @Test void googleOnlyCannotUsePasswordLogin() {
        when(users.findByUsername("writer")).thenReturn(Optional.of(User.builder().fullName("Google").build()));
        assertThatThrownBy(()->auth.login(new LoginRequest("writer","wrong"))).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }
    @Test void temporaryPasswordOnlyRestrictsPasswordSessions() {
        var u=User.builder().fullName("Writer").passwordHash("hash").mustChangePassword(true).build();
        assertThat(auth.session(u,"PASSWORD").requiresPasswordChange()).isTrue();
        assertThat(auth.session(u,"GOOGLE").requiresPasswordChange()).isFalse();
        assertThat(u.isMustChangePassword()).isTrue();
    }
}
