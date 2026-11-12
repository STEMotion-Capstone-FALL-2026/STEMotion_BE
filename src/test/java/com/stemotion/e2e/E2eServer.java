package com.stemotion.e2e;

import com.stemotion.domain.auth.entity.SystemRole;

import com.stemotion.StemotionApplication;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.integration.config.IntegrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Back end for the browser end-to-end tests (STEMotion_FE/e2e). Runs the real application on a
 * throwaway embedded PostgreSQL built by the Flyway migrations, so a test run never touches the
 * shared database. Lives in test scope: it is not packaged into the deployed jar.
 *
 * Every secret and outside service is overridden on the command line, which outranks the .env
 * file loaded by spring-dotenv: no paid AI, TTS or render call can leave this process. AI and the
 * render worker are answered by {@link E2eFakes}, so the workflows still run end to end.
 *
 * Start: mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.stemotion.e2e.E2eServer
 */
public class E2eServer {

    static final String ADMIN_USERNAME = env("E2E_ADMIN_USERNAME", "e2e-admin");
    static final String ADMIN_PASSWORD = env("E2E_ADMIN_PASSWORD", "E2e-fixture-only-123");

    public static void main(String[] args) {
        // DevTools would relaunch main with these arguments added a second time.
        System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.from(StemotionApplication::main).with(Config.class).run(launchArguments(args));
    }

    static String[] launchArguments(String... args) {
        boolean stem = "1".equals(env("E2E_STEM", "0"));
        String flywayLocations = stem
            ? "classpath:db/migration,classpath:db/auth-v2-e2e,classpath:db/stem-p3,classpath:db/stem-p5"
            : "classpath:db/migration,classpath:db/auth-v2-e2e";
        List<String> all = new ArrayList<>(List.of(
            "--server.port=" + env("E2E_API_PORT", "18080"),
            "--server.address=127.0.0.1",
            "--spring.flyway.enabled=true",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.flyway.locations=" + flywayLocations,
            "--app.auth-v2.enabled=true",
            "--app.notification-email.enabled=true",
            "--app.notification-email.provider=local",
            "--app.notification-email.api-key=",
            "--app.notification-email.public-url=http://localhost:3000",
            "--app.auth-v2.rate-limit-attempts=500",
            "--app.auth-v2.google-enabled=true",
            "--spring.datasource.url=unused-replaced-by-embedded-postgres",
            "--spring.datasource.username=postgres",
            "--spring.datasource.password=",
            "--app.jwt.secret=e2e-only-jwt-secret-not-used-anywhere-else-0123456789",
            "--app.cors.allowed-origins=http://localhost:*,http://127.0.0.1:*",
            "--app.integration.google.client-id=",
            "--app.integration.ai.mode=embedded",
            "--app.integration.ai.base-url=http://127.0.0.1:9",
            "--app.integration.ai.api-key=e2e-unused",
            "--app.integration.ai.alignment-worker-key=e2e-unused",
            "--app.integration.gemini.enabled=false",
            "--app.integration.gemini.api-key=",
            "--app.integration.render.base-url=http://127.0.0.1:9",
            "--app.integration.render.api-key=e2e-worker-key",
            "--app.integration.render.callback-url=http://127.0.0.1:" + env("E2E_API_PORT", "18080") + "/api/v1/render/callback",
            "--app.integration.render.max-attempts=1",
            "--AI_PRICING_JSON={}",
            "--app.video.storyboard-enabled=false",
            "--logging.level.org.hibernate.SQL=WARN",
            "--logging.level.com.stemotion=INFO"));
        if (stem) {
            all.add("--app.stem.enabled=true");
            all.add("--app.stem.artifact.enabled=true");
        }
        // Spring joins repeated command-line option values with commas; later entries do
        // not replace earlier ones. Remove overridden defaults before adding user arguments.
        for (String arg : args) {
            String option = arg.split("=", 2)[0];
            all.removeIf(defaultArg -> defaultArg.split("=", 2)[0].equals(option));
        }
        all.addAll(List.of(args));
        return all.toArray(String[]::new);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean(destroyMethod = "close")
        EmbeddedPostgres e2ePostgres() throws IOException {
            return EmbeddedPostgres.builder().start();
        }

        /** stringtype=unspecified as in application.yml: string ids bind to uuid columns. */
        @Bean
        DataSource dataSource(EmbeddedPostgres postgres) {
            return DataSourceBuilder.create()
                .url(postgres.getJdbcUrl("postgres", "postgres") + "&stringtype=unspecified")
                .username("postgres")
                .password("")
                .build();
        }

        @Bean
        @Primary
        E2eFakes.FakeGemini e2eGemini(IntegrationProperties properties, ObjectMapper mapper) {
            return new E2eFakes.FakeGemini(properties, mapper);
        }

        /** Test classpath only. No fake-token branch exists in the production verifier. */
        @Bean @Primary
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name="e2e.google.fake", havingValue="true", matchIfMissing=true)
        E2eGoogleVerifier e2eGoogleVerifier(IntegrationProperties properties) {
            return new E2eGoogleVerifier(properties);
        }

        @Bean
        @Primary
        E2eFakes.FakeRenderWorker e2eRenderWorker(IntegrationProperties properties, ObjectMapper mapper) {
            return new E2eFakes.FakeRenderWorker(properties, mapper);
        }

        /** The one account the tests start from; everything else is provisioned through the API. */
        @Bean
        @org.springframework.core.annotation.Order(10)
        ApplicationRunner seedAdmin(UserRepository users, PasswordEncoder encoder, DataSource dataSource,
            @org.springframework.beans.factory.annotation.Value("${e2e.restore-snapshot:}") String snapshot) {
            return args -> {
                if (!snapshot.isBlank()) {
                    // This test configuration always owns a NEW embedded DB; never the shared datasource.
                    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                        String restored = java.nio.file.Files.readString(java.nio.file.Path.of(snapshot));
                        statement.execute(restored);
                        if (!restored.contains("\"system_role\"")) {
                            statement.execute("UPDATE users SET system_role=CASE WHEN role::text='ADMIN' THEN 'ADMIN' ELSE 'USER' END,auth_version=auth_version+1");
                        }
                    }
                }
                if (users.existsByUsername(ADMIN_USERNAME)) return;
                users.save(User.builder()
                    .fullName("E2E Admin")
                    .username(ADMIN_USERNAME)
                    .passwordHash(encoder.encode(ADMIN_PASSWORD))
                    .systemRole(SystemRole.ADMIN)
                    .status(User.UserStatus.ACTIVE)
                    .mustChangePassword(false)
                    .build());
            };
        }

        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "app.stem.enabled", havingValue = "true")
        @org.springframework.core.annotation.Order(20)
        ApplicationRunner seedStemCatalog(com.stemotion.domain.stem.StemService stemService, UserRepository users) {
            return args -> {
                var admin = users.findByUsername(ADMIN_USERNAME).orElse(null);
                if (admin == null) return;
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var req = mapper.createObjectNode()
                    .put("pack", "stem-demo-pack.v1")
                    .put("idempotencyKey", "e2e-seed-demo-pack-v1");
                try {
                    stemService.importCatalog(admin.getId(), req);
                } catch (Exception ignored) {
                }
            };
        }
    }

    static String env(String name, String fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
