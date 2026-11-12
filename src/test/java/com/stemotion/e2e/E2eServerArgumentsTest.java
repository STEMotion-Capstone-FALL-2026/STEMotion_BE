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

    @Test
    void defaultArgumentsDoNotIncludeStem() {
        var source = new SimpleCommandLinePropertySource(E2eServer.launchArguments());
        assertThat(source.containsProperty("app.stem.enabled")).isFalse();
        assertThat(source.containsProperty("app.stem.artifact.enabled")).isFalse();
        assertThat(source.getProperty("spring.flyway.locations"))
            .isEqualTo("classpath:db/migration,classpath:db/auth-v2-e2e");
    }

    @Test
    void optInStemIncludesStemFlagsAndFlywayLocations() {
        System.setProperty("E2E_STEM", "1");
        try {
            var source = new SimpleCommandLinePropertySource(E2eServer.launchArguments());
            assertThat(source.getProperty("app.stem.enabled")).isEqualTo("true");
            assertThat(source.getProperty("app.stem.artifact.enabled")).isEqualTo("true");
            assertThat(source.getProperty("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration,classpath:db/auth-v2-e2e,classpath:db/stem-p3,classpath:db/stem-p5");
        } finally {
            System.clearProperty("E2E_STEM");
        }
    }
}
