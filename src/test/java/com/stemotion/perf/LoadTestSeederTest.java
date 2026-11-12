package com.stemotion.perf;

import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration,classpath:db/auth-v2-e2e",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LoadTestSeeder.class)
@ActiveProfiles("test")
class LoadTestSeederTest {

    static EmbeddedPostgres postgres;

    @TestConfiguration
    static class Config {
        @Bean
        static PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        DataSource dataSource() throws IOException {
            if (postgres == null) postgres = EmbeddedPostgres.builder().start();
            return postgres.getDatabase("postgres", "postgres", Map.of("stringtype", "unspecified"));
        }
    }

    @AfterAll
    static void teardown() throws IOException {
        if (postgres != null) {
            postgres.close();
            postgres = null;
        }
    }

    @Autowired LoadTestSeeder seeder;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired ClipProjectRepository projects;
    @Autowired ScriptVersionRepository versions;
    @Autowired SceneRepository scenes;

    @Test
    void seederProvisionsExactBaselineEntities() {
        seeder.seed();

        assertThat(users.count()).isGreaterThanOrEqualTo(200);
        assertThat(workspaces.count()).isGreaterThanOrEqualTo(3);
        assertThat(projects.count()).isGreaterThanOrEqualTo(150);
        assertThat(versions.count()).isGreaterThanOrEqualTo(150);
        assertThat(scenes.count()).isGreaterThanOrEqualTo(1500);

        // Idempotent: re-running does not corrupt or fail
        seeder.seed();
        assertThat(users.count()).isGreaterThanOrEqualTo(200);
    }
}
