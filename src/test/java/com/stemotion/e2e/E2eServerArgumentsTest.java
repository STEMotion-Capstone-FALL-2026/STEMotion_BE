package com.stemotion.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.SimpleCommandLinePropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class E2eServerArgumentsTest {
    @Test
    void realGoogleClientOverridesTheEmptyFixtureDefaultWithoutCommaJoining() {
        var source = new SimpleCommandLinePropertySource(E2eServer.launchArguments(
            "--e2e.google.fake=false",
            "--app.integration.google.client-id=fixture.apps.googleusercontent.com"));
        assertThat(source.getProperty("app.integration.google.client-id"))
            .isEqualTo("fixture.apps.googleusercontent.com");
        assertThat(source.getProperty("e2e.google.fake")).isEqualTo("false");
        assertThat(source.getProperty("spring.datasource.url"))
            .isEqualTo("unused-replaced-by-embedded-postgres");
    }
}
