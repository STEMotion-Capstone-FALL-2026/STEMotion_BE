package com.stemotion.e2e;

import com.stemotion.StemotionApplication;
import com.stemotion.domain.auth.entity.SystemRole;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class StemSmokePythonIntegrationTest {

    private static ConfigurableApplicationContext context;
    private static int port;
    private static String workspaceId;

    @BeforeAll
    static void startServer() {
        System.setProperty("spring.devtools.restart.enabled", "false");
        context = new SpringApplication(StemotionApplication.class, E2eServer.Config.class)
            .run(E2eServer.launchArguments(
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.profiles.active=stem-p3-test", "--spring.dotenv.enabled=false",
                "--spring.flyway.locations=classpath:db/migration,classpath:db/auth-v2-e2e,classpath:db/stem-p3",
                "--app.stem.enabled=true", "--app.notification-email.enabled=false",
                "--logging.level.root=WARN", "--logging.level.com.stemotion=WARN",
                "--logging.level.org.hibernate.SQL=WARN",
                "--logging.level.io.zonky.test.db.postgres.embedded.EmbeddedPostgres=INFO"));

        port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();

        var users = context.getBean(UserRepository.class);
        var encoder = context.getBean(PasswordEncoder.class);
        var workspaces = context.getBean(WorkspaceRepository.class);
        var members = context.getBean(WorkspaceMemberRepository.class);
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

        tx.executeWithoutResult(status -> {
            var admin = users.findByUsername("e2e-admin").orElseThrow();

            var writer = users.saveAndFlush(User.builder()
                .fullName("TEST_ONLY smoke writer")
                .username("smoke-writer")
                .passwordHash(encoder.encode("Pass-writer-123"))
                .systemRole(SystemRole.USER)
                .status(User.UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

            var reviewer = users.saveAndFlush(User.builder()
                .fullName("TEST_ONLY smoke reviewer")
                .username("smoke-reviewer")
                .passwordHash(encoder.encode("Pass-reviewer-123"))
                .systemRole(SystemRole.USER)
                .status(User.UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

            var producer = users.saveAndFlush(User.builder()
                .fullName("TEST_ONLY smoke producer")
                .username("smoke-producer")
                .passwordHash(encoder.encode("Pass-producer-123"))
                .systemRole(SystemRole.USER)
                .status(User.UserStatus.ACTIVE)
                .mustChangePassword(false)
                .build());

            var ws = workspaces.saveAndFlush(Workspace.builder()
                .name("TEST_ONLY STEM Smoke Workspace " + UUID.randomUUID())
                .createdBy(admin)
                .build());
            workspaceId = ws.getId();

            members.saveAndFlush(WorkspaceMember.builder()
                .workspace(ws).user(writer).role(WorkspaceRole.WRITER).isLeader(false).build());
            members.saveAndFlush(WorkspaceMember.builder()
                .workspace(ws).user(reviewer).role(WorkspaceRole.REVIEWER).isLeader(false).build());
            members.saveAndFlush(WorkspaceMember.builder()
                .workspace(ws).user(producer).role(WorkspaceRole.PRODUCER).isLeader(false).build());
        });
    }

    @AfterAll
    static void stopServer() {
        if (context != null) context.close();
    }

    @Test
    void testSmokeStemPythonScript() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "python",
            "deploy/stem-demo/smoke-stem.py",
            "--base-url", "http://127.0.0.1:" + port,
            "--workspace", workspaceId,
            "--level", "PRIMARY",
            "--i-understand-this-writes-demo-data"
        );

        pb.environment().put("STEM_SMOKE_ADMIN_USER", "e2e-admin");
        pb.environment().put("STEM_SMOKE_ADMIN_PASS", "E2e-fixture-only-123");
        pb.environment().put("STEM_SMOKE_WRITER_USER", "smoke-writer");
        pb.environment().put("STEM_SMOKE_WRITER_PASS", "Pass-writer-123");
        pb.environment().put("STEM_SMOKE_REVIEWER_USER", "smoke-reviewer");
        pb.environment().put("STEM_SMOKE_REVIEWER_PASS", "Pass-reviewer-123");
        pb.environment().put("STEM_SMOKE_PRODUCER_USER", "smoke-producer");
        pb.environment().put("STEM_SMOKE_PRODUCER_PASS", "Pass-producer-123");

        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                System.out.println(line);
            }
        }

        int exitCode = p.waitFor();
        assertThat(exitCode).as("Output: " + sb).isZero();
        assertThat(sb.toString()).contains("OVERALL RESULT: ALL PASS");
    }
}
