package com.stemotion.integration.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** Read real profile YAML without creating a datasource or calling an AI provider. */
class AiSharedKeyConfigurationTest {
    @Test void bothProfilesPreferSharedKeyAndPreserveLegacyFallback() throws Exception {
        for (String resource : new String[]{"application.yml", "application-cloud.yml"}) {
            var env = new StandardEnvironment();
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            for (var source : new YamlPropertySourceLoader().load("ai-config", new ClassPathResource(resource))) {
                env.getPropertySources().addLast(source);
            }
            env.getPropertySources().addFirst(new MapPropertySource("test-keys", Map.of(
                "AI_SERVICE_SHARED_KEY", "new-test-key", "AI_SERVICE_KEY", "legacy-test-key")));
            assertThat(env.getProperty("app.integration.ai.api-key")).isEqualTo("new-test-key");
            env.getPropertySources().replace("test-keys", new MapPropertySource("test-keys", Map.of("AI_SERVICE_KEY", "legacy-test-key")));
            assertThat(env.getProperty("app.integration.ai.api-key")).isEqualTo("legacy-test-key");
            env.getPropertySources().remove("test-keys");
            assertThat(env.getProperty("app.integration.ai.api-key")).isEmpty();
        }
    }
}
