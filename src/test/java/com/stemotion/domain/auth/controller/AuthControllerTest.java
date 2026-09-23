package com.stemotion.domain.auth.controller;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * /auth/me used to sit under the blanket permitAll for /api/v1/auth/**, so an
 * expired token reached the controller with a null principal and crashed with
 * a 500 instead of telling the browser to sign in again. The route is
 * protected again; this pins the controller's own guard as a second line.
 */
class AuthControllerTest {

    private final AuthController controller = new AuthController(mock(AuthService.class));

    @Test
    @DisplayName("a missing principal is rejected as 401, never a 500")
    void nullPrincipalIsUnauthorized() {
        assertThatThrownBy(() -> controller.me(null))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("the message tells the user to sign in again")
    void messageAsksForANewSession() {
        assertThatThrownBy(() -> controller.me(null))
            .hasMessageContaining("đăng nhập lại");
    }
}
