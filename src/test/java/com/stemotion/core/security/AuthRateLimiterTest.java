package com.stemotion.core.security;
import com.stemotion.core.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthRateLimiterTest {
    @Test void independentKeysDoNotResetTheAccountBucket() {
        var limiter=new AuthRateLimiter();
        for(int n=0;n<10;n++)limiter.check("username:writer");
        limiter.check("ip:another-address");
        assertThatThrownBy(()->limiter.check("username:writer")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getStatus().value()).isEqualTo(429));
    }
}
