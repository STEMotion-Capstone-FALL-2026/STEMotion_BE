package com.stemotion.core.security;
import com.stemotion.domain.auth.entity.User;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthSessionVersionTest {
    @Test void credentialMutationInvalidatesPreviouslyIssuedTokens() {
        var jwt=new JwtService("auth-v2-test-secret-long-enough-for-hmac-signing-0123456789",60000);
        var user=User.builder().username("writer").passwordHash("fixture-hash").build();user.setId("u1");
        String token=jwt.generateToken(user);
        assertThat(jwt.session(token,user).method()).isEqualTo("PASSWORD");
        user.setAuthVersion(1);
        assertThatThrownBy(()->jwt.session(token,user)).isInstanceOf(IllegalArgumentException.class);
    }
}
