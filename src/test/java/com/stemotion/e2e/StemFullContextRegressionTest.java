package com.stemotion.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.StemotionApplication;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.domain.auth.entity.SystemRole;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.stem.StemDtos;
import com.stemotion.domain.stem.StemModels;
import com.stemotion.domain.stem.StemRepository;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationAdvisor;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Full application, real proxy/JWT/HTTP/PG. A slice or standalone controller hides this regression. */
class StemFullContextRegressionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static String base;
    private static String writerId;
    private static String writerToken;
    private static String adminToken;
    private String workspace;
    private String project;
    private String version;

    @BeforeAll
    static void bootFullApplication() throws Exception {
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
        base = "http://127.0.0.1:"
            + ((ServletWebServerApplicationContext) context).getWebServer().getPort() + "/api/v1";
        jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class))
            .isEqualTo(26);
        assertThat(context.getBeansOfType(PersistenceExceptionTranslationPostProcessor.class)).isNotEmpty();
        StemRepository repository = context.getBean(StemRepository.class);
        assertThat(AopUtils.isAopProxy(repository)).isTrue();
        assertThat(Arrays.stream(((Advised) repository).getAdvisors())
            .anyMatch(PersistenceExceptionTranslationAdvisor.class::isInstance)).isTrue();

        writerId = new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status ->
            context.getBean(UserRepository.class).saveAndFlush(User.builder()
                .fullName("TEST_ONLY full context writer").username("fix-writer")
                .passwordHash(context.getBean(PasswordEncoder.class).encode("Fix-fixture-only-123"))
                .systemRole(SystemRole.USER).status(User.UserStatus.ACTIVE).mustChangePassword(false)
                .build()).getId());
        adminToken = login("e2e-admin", "E2e-fixture-only-123");
        writerToken = login("fix-writer", "Fix-fixture-only-123");
    }

    @AfterAll
    static void closeOnlyOwnedContextAndDatabase() {
        if (context != null) context.close(); // E2eServer.Config owns EmbeddedPostgres with destroyMethod=close.
    }

    @BeforeEach
    void freshTestOnlyProject() {
        Map<String, String> ids = new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
            .execute(status -> {
                var users = context.getBean(UserRepository.class);
                User admin = users.findByUsername("e2e-admin").orElseThrow();
                User writer = users.findById(writerId).orElseThrow();
                Workspace ws = context.getBean(WorkspaceRepository.class).saveAndFlush(Workspace.builder()
                    .name("TEST_ONLY full context " + UUID.randomUUID()).createdBy(admin).build());
                context.getBean(WorkspaceMemberRepository.class).saveAndFlush(WorkspaceMember.builder()
                    .workspace(ws).user(writer).role(WorkspaceRole.WRITER).build());
                ClipProject cp = context.getBean(ClipProjectRepository.class).saveAndFlush(ClipProject.builder()
                    .title("TEST_ONLY not a verified lesson").subject(StemSubject.Math).gradeLevel("4")
                    .owner(writer).workspace(ws).build());
                ScriptVersion sv = context.getBean(ScriptVersionRepository.class).saveAndFlush(ScriptVersion.builder()
                    .project(cp).title("TEST_ONLY").versionNo(1).createdBy(writer).build());
                context.getBean(SceneRepository.class).saveAndFlush(Scene.builder().scriptVersion(sv)
                    .sceneType(SceneType.TITLE_HERO).title("TEST_ONLY").narration("TEST_ONLY")
                    .props("{\"subtitle\":\"TEST_ONLY\",\"subject\":\"Math\",\"gradeLevel\":\"4\",\"badgeText\":\"TEST_ONLY\",\"durationInFrames\":90}")
                    .orderNo(1).build());
                context.getBean(StemRepository.class).createDraft(StemModels.Kind.POLICY,
                    "TEST_ONLY-" + UUID.randomUUID(), 1, null, null, JSON.createObjectNode());
                return Map.of("workspace", ws.getId(), "project", cp.getId(), "version", sv.getId());
            });
        workspace = ids.get("workspace");
        project = ids.get("project");
        version = ids.get("version");
    }

    @Test
    void settingsReadThroughRealProxyReturnsFreshWorkspaceMode() throws Exception {
        var response = call("GET", "/workspaces/" + workspace + "/stem-settings", null, adminToken);
        assertThat(response.statusCode()).as("GET settings must not fail at StemService.workspace").isEqualTo(200);
        JsonNode mode = JSON.readTree(response.body());
        assertThat(mode.path("mode").asText()).isEqualTo("OFF");
        assertThat(mode.path("revision").asInt()).isZero();
        assertThat(mode.path("policyIds").isArray()).isTrue();
        assertThat(mode.path("policyIds").isEmpty()).isTrue();
        assertThat(mode.has("defaultPolicyId")).isTrue();
        assertThat(mode.has("modeRevision")).isFalse();
    }

    @Test
    void createThroughRealProxyIsNullableIdempotentAndRetainsGuards() throws Exception {
        Map<String, Object> create = Map.of("schemaVersion", "stem.v1", "expectedScriptRevision", 0,
            "idempotencyKey", "TEST_ONLY_create_" + version);
        var first = call("POST", stemPath(), create, writerToken);
        assertThat(first.statusCode()).as("POST create must not fail at StemService.create").isEqualTo(200);
        JsonNode brief = JSON.readTree(first.body());
        assertThat(brief.path("revision").asInt()).isZero();
        assertThat(brief.path("brief").path("context").path("primary").isNull()).isTrue();
        assertThat(brief.path("verificationStatus").asText()).isEqualTo("UNVERIFIED");
        assertThat(call("POST", stemPath(), create, writerToken).body()).isEqualTo(first.body());
        assertThat(jdbc.queryForObject("select count(*) from stem_briefs where version_id=?", Integer.class, version))
            .isEqualTo(1);

        Map<String, Object> save = new LinkedHashMap<>(Map.of("schemaVersion", "stem.v1", "baseRevision", 7,
            "expectedScriptRevision", 0, "idempotencyKey", "TEST_ONLY_stale_" + version,
            "brief", StemDtos.empty(), "evidence", List.of()));
        assertThat(call("PUT", stemPath(), save, writerToken).statusCode()).isEqualTo(409);
        save.put("baseRevision", 0);
        save.put("verified", true);
        assertThat(call("PUT", stemPath(), save, writerToken).statusCode()).isEqualTo(400);
        assertThat(call("POST", stemPath(), create, adminToken).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/scripts/" + project + "/versions/" + UUID.randomUUID() + "/stem",
            null, writerToken).statusCode()).isEqualTo(404);
    }

    @Test
    void catalogAndLegacyDraftStillWorkAndSubmitTraversesTheRealStemHook() throws Exception {
        assertThat(call("GET", "/stem/catalog", null, writerToken).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/stem/frameworks", null, adminToken).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/stem/frameworks", null, writerToken).statusCode()).isEqualTo(403);
        assertThat(call("GET", stemPath(), null, writerToken).statusCode()).isEqualTo(200);
        var script = call("GET", "/scripts/" + project, null, writerToken);
        assertThat(script.statusCode()).isEqualTo(200);
        assertThat(call("PUT", "/scripts/" + project,
            Map.of("baseRevision", 0, "scenes", JSON.readTree(script.body()).path("scenes")), writerToken)
            .statusCode()).isEqualTo(200);
        var submitted = call("POST", "/scripts/" + project + "/submit-review", Map.of(), writerToken);
        assertThat(submitted.statusCode()).as("legacy submit must not fail at StemService.lock").isEqualTo(200);
        assertThat(JSON.readTree(submitted.body()).path("scriptStatus").asText()).isEqualTo("IN_REVIEW");
    }

    private String stemPath() {
        return "/scripts/" + project + "/versions/" + version + "/stem";
    }

    private static String login(String username, String password) throws Exception {
        var response = call("POST", "/auth/login", Map.of("username", username, "password", password), null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode node = JSON.readTree(response.body());
        return (node.has("data") ? node.get("data") : node).path("accessToken").asText();
    }

    private static HttpResponse<String> call(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HTTP.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
