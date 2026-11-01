package com.stemotion.e2e;

import com.stemotion.StemotionApplication;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
 * file loaded by spring-dotenv: no paid AI, TTS or render call can leave this process.
 *
 * Start: mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.stemotion.e2e.E2eServer
 */
public class E2eServer {

    static final String ADMIN_EMAIL = env("E2E_ADMIN_EMAIL", "e2e-admin@example.invalid");
    static final String ADMIN_PASSWORD = env("E2E_ADMIN_PASSWORD", "E2e-fixture-only-123");

    public static void main(String[] args) {
        // DevTools would relaunch main with these arguments added a second time.
        System.setProperty("spring.devtools.restart.enabled", "false");
        List<String> all = new ArrayList<>(List.of(
            "--server.port=" + env("E2E_API_PORT", "18080"),
            "--spring.flyway.enabled=true",
            "--spring.jpa.hibernate.ddl-auto=validate",
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
            "--app.integration.render.max-attempts=1",
            "--AI_PRICING_JSON={}",
            "--app.video.storyboard-enabled=false",
            "--logging.level.org.hibernate.SQL=WARN",
            "--logging.level.com.stemotion=INFO"));
        all.addAll(List.of(args));
        SpringApplication.from(StemotionApplication::main).with(Config.class).run(all.toArray(String[]::new));
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

        /** The one account the tests start from; everything else is provisioned through the API. */
        @Bean
        ApplicationRunner seedAdmin(UserRepository users, PasswordEncoder encoder) {
            return args -> {
                if (users.existsByEmailIgnoreCase(ADMIN_EMAIL)) return;
                users.save(User.builder()
                    .fullName("E2E Admin")
                    .email(ADMIN_EMAIL)
                    .passwordHash(encoder.encode(ADMIN_PASSWORD))
                    .role(User.UserRole.ADMIN)
                    .status(User.UserStatus.ACTIVE)
                    .mustChangePassword(false)
                    .build());
            };
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
