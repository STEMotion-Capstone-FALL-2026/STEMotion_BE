package com.stemotion.domain.youtube;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.SystemRole;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.PublishRequest;
import com.stemotion.domain.production.dto.ProductionDtos.PublishResponse;
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.Publication;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.ClipRepository;
import com.stemotion.domain.production.repository.PublicationRepository;
import com.stemotion.domain.production.repository.PublishRecordRepository;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.production.service.ClipArtifactService;
import com.stemotion.domain.production.service.PublishService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.review.dto.QaDtos.QaReport;
import com.stemotion.domain.review.service.ClipQaService;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.stem.StemRepository;
import com.stemotion.domain.stem.StemService;
import com.stemotion.domain.stem.StemValidator;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import com.stemotion.domain.youtube.config.YoutubeProperties;
import com.stemotion.domain.youtube.dto.YoutubeDtos.ConnectResponse;
import com.stemotion.domain.youtube.dto.YoutubeDtos.ConnectionStatusResponse;
import com.stemotion.domain.youtube.entity.YoutubeChannelConnection;
import com.stemotion.domain.youtube.repository.YoutubeChannelConnectionRepository;
import com.stemotion.domain.youtube.service.TokenEncryptionService;
import com.stemotion.domain.youtube.service.YoutubeOAuthService;
import com.stemotion.domain.youtube.service.YoutubeStateService;
import com.stemotion.domain.youtube.service.YoutubeUploadService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
    "app.stem.enabled=true",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@ActiveProfiles("stem-p3-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PublishService.class,
    YoutubeOAuthService.class,
    YoutubeUploadService.class,
    YoutubeStateService.class,
    TokenEncryptionService.class,
    YoutubeProperties.class,
    ClipArtifactService.class,
    WorkspaceAccess.class,
    StemRepository.class,
    StemValidator.class,
    StemService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class YoutubePublishAcceptanceTest {

    static EmbeddedPostgres pg;
    static HttpServer fakeGoogleServer;
    static int fakeServerPort;

    static AtomicInteger tokenAttempts = new AtomicInteger(0);
    static AtomicInteger initUploadAttempts = new AtomicInteger(0);
    static AtomicInteger putVideoAttempts = new AtomicInteger(0);
    static String lastInitiatedMetadata = "";
    static boolean simulateQuotaExceeded = false;
    static boolean simulateRevokedToken = false;
    static boolean simulate5xxThenSuccess = false;

    @TestConfiguration
    static class Config {
        @Bean
        DataSource dataSource() throws Exception {
            pg = EmbeddedPostgres.builder().start();
            return pg.getDatabase("postgres", "postgres", Map.of("stringtype", "unspecified"));
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @BeforeAll
    static void startFakeGoogleServer() throws Exception {
        fakeGoogleServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeServerPort = fakeGoogleServer.getAddress().getPort();

        // 1. Token Endpoint
        fakeGoogleServer.createContext("/token", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                tokenAttempts.incrementAndGet();
                byte[] reqBytes = exchange.getRequestBody().readAllBytes();
                String body = new String(reqBytes, StandardCharsets.UTF_8);

                if (simulateRevokedToken) {
                    sendJson(exchange, 401, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}");
                    return;
                }

                if (body.contains("grant_type=authorization_code")) {
                    sendJson(exchange, 200, "{\"access_token\":\"mock-access-token-123\",\"refresh_token\":\"mock-refresh-token-xyz\",\"expires_in\":3600,\"scope\":\"https://www.googleapis.com/auth/youtube.upload\"}");
                } else if (body.contains("grant_type=refresh_token")) {
                    sendJson(exchange, 200, "{\"access_token\":\"mock-refreshed-access-token-456\",\"expires_in\":3600}");
                } else {
                    sendJson(exchange, 400, "{\"error\":\"unsupported_grant_type\"}");
                }
            }
        });

        // 2. Channel Endpoint
        fakeGoogleServer.createContext("/youtube/v3/channels", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                sendJson(exchange, 200, "{\"items\":[{\"id\":\"UC_TEST_CHANNEL_123\",\"snippet\":{\"title\":\"STEMotion Educational Channel\"}}]}");
            }
        });

        // 3. Resumable Upload Initiation
        fakeGoogleServer.createContext("/upload/youtube/v3/videos", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                initUploadAttempts.incrementAndGet();
                byte[] reqBytes = exchange.getRequestBody().readAllBytes();
                lastInitiatedMetadata = new String(reqBytes, StandardCharsets.UTF_8);

                if (simulateQuotaExceeded) {
                    sendJson(exchange, 403, "{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}],\"message\":\"The request cannot be completed because you have exceeded your quota.\",\"code\":403}}");
                    return;
                }

                if (simulate5xxThenSuccess && initUploadAttempts.get() == 1) {
                    sendJson(exchange, 503, "{\"error\":\"Service Temporarily Unavailable\"}");
                    return;
                }

                exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + fakeServerPort + "/upload-session/session_id_999");
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });

        // 4. Video Transfer Endpoint
        fakeGoogleServer.createContext("/upload-session/session_id_999", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                putVideoAttempts.incrementAndGet();
                exchange.getRequestBody().readAllBytes();

                if (simulate5xxThenSuccess && putVideoAttempts.get() == 1) {
                    sendJson(exchange, 500, "{\"error\":\"Internal Server Error during upload\"}");
                    return;
                }

                sendJson(exchange, 200, "{\"id\":\"yt_vid_test_xyz789\",\"snippet\":{\"title\":\"STEM Video\"}}");
            }
        });

        // 5. Revoke Endpoint
        fakeGoogleServer.createContext("/revoke", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            }
        });

        fakeGoogleServer.setExecutor(null);
        fakeGoogleServer.start();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        if (fakeGoogleServer != null) fakeGoogleServer.stop(0);
        if (pg != null) pg.close();
    }

    @MockBean AuthService authService;
    @MockBean ClipQaService clipQaService;
    @MockBean ScriptService scriptService;

    @Autowired PublishService publishService;
    @Autowired YoutubeOAuthService oauthService;
    @Autowired YoutubeUploadService uploadService;
    @Autowired YoutubeProperties youtubeProperties;
    @Autowired YoutubeChannelConnectionRepository connectionRepository;
    @Autowired PublicationRepository publicationRepository;
    @Autowired PublishRecordRepository publishRecordRepository;
    @Autowired TokenEncryptionService encryptionService;
    @Autowired YoutubeStateService stateService;
    @Autowired ClipArtifactService clipArtifactService;
    @Autowired StemService stemService;
    @Autowired JdbcTemplate jdbc;

    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;
    @Autowired ClipProjectRepository projects;
    @Autowired ScriptVersionRepository versions;
    @Autowired SceneRepository scenes;
    @Autowired RenderJobRepository renderJobs;
    @Autowired ClipRepository clips;
    @Autowired PlatformTransactionManager manager;

    TransactionTemplate tx;
    String adminId, writerId, producerId, workspaceId, projectId, versionId, clipId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(manager);

        // Reset server simulation flags
        tokenAttempts.set(0);
        initUploadAttempts.set(0);
        putVideoAttempts.set(0);
        lastInitiatedMetadata = "";
        simulateQuotaExceeded = false;
        simulateRevokedToken = false;
        simulate5xxThenSuccess = false;

        // Configure properties to point to fake server
        youtubeProperties.setEnabled(true);
        youtubeProperties.setClientId("test-client-id");
        youtubeProperties.setClientSecret("test-client-secret");
        youtubeProperties.setTokenKey("0123456789abcdef0123456789abcdef");
        youtubeProperties.setRedirectUri("http://localhost:8080/api/v1/youtube/oauth/callback");
        youtubeProperties.setReturnUrl("http://localhost:3000/workspaces/%s/settings");
        youtubeProperties.setAuthUrl("http://127.0.0.1:" + fakeServerPort + "/auth");
        youtubeProperties.setTokenUrl("http://127.0.0.1:" + fakeServerPort + "/token");
        youtubeProperties.setRevokeUrl("http://127.0.0.1:" + fakeServerPort + "/revoke");
        youtubeProperties.setApiBaseUrl("http://127.0.0.1:" + fakeServerPort);
        youtubeProperties.setUploadBaseUrl("http://127.0.0.1:" + fakeServerPort + "/upload/youtube/v3/videos");

        // Set up Mockito answers
        when(authService.findUserOrThrow(anyString())).thenAnswer(inv -> users.findById(inv.getArgument(0)).orElseThrow());
        when(clipQaService.run(anyString())).thenAnswer(inv -> new QaReport(inv.getArgument(0), true, 0, 0, List.of()));
        when(scriptService.getOrThrow(anyString())).thenAnswer(inv -> projects.findById(inv.getArgument(0)).orElseThrow());
        when(scriptService.currentVersion(any())).thenAnswer(inv -> versions.findById(versionId).orElseThrow());

        tx.executeWithoutResult(status -> {
            publicationRepository.deleteAll();
            publishRecordRepository.deleteAll();
            connectionRepository.deleteAll();

            User admin = users.saveAndFlush(User.builder()
                .username("yt-admin-" + UUID.randomUUID().toString().substring(0, 10))
                .fullName("Test Admin")
                .email("admin-" + UUID.randomUUID() + "@example.com")
                .systemRole(SystemRole.ADMIN)
                .authVersion(1L)
                .build());
            adminId = admin.getId();

            User writer = users.saveAndFlush(User.builder()
                .username("yt-writer-" + UUID.randomUUID().toString().substring(0, 10))
                .fullName("Test Writer")
                .email("writer-" + UUID.randomUUID() + "@example.com")
                .systemRole(SystemRole.USER)
                .authVersion(1L)
                .build());
            writerId = writer.getId();

            User producer = users.saveAndFlush(User.builder()
                .username("yt-producer-" + UUID.randomUUID().toString().substring(0, 10))
                .fullName("Test Producer")
                .email("producer-" + UUID.randomUUID() + "@example.com")
                .systemRole(SystemRole.USER)
                .authVersion(1L)
                .build());
            producerId = producer.getId();

            Workspace ws = workspaces.saveAndFlush(Workspace.builder()
                .name("YT Workspace " + UUID.randomUUID())
                .createdBy(admin)
                .build());
            workspaceId = ws.getId();

            members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(writer).role(WorkspaceRole.WRITER).build());
            members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(producer).role(WorkspaceRole.PRODUCER).build());

            ClipProject cp = projects.saveAndFlush(ClipProject.builder()
                .title("STEM Lesson: Newton Laws")
                .subject(StemSubject.Physics)
                .gradeLevel("GRADE_10")
                .owner(writer)
                .workspace(ws)
                .videoStatus(VideoStatus.APPROVED)
                .videoUrl("https://storage.googleapis.com/test-bucket/video.mp4")
                .build());
            projectId = cp.getId();

            ScriptVersion sv = versions.saveAndFlush(ScriptVersion.builder()
                .project(cp)
                .createdBy(writer)
                .versionNo(1)
                .title("Test Version")
                .isCurrent(true)
                .build());
            versionId = sv.getId();

            RenderJob job = renderJobs.saveAndFlush(RenderJob.builder()
                .project(cp)
                .scriptVersion(sv)
                .requestedBy(producer)
                .status(RenderStatus.COMPLETED)
                .progress(100)
                .videoUrl(cp.getVideoUrl())
                .resolution(RenderResolution.RES_1080P)
                .fps(30)
                .build());

            Clip c = clips.saveAndFlush(Clip.builder()
                .renderJob(job)
                .videoUrl(cp.getVideoUrl())
                .build());
            clipId = c.getId();
        });
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void connectWorkspaceToYoutube() {
        ConnectResponse connectResp = oauthService.buildAuthorizationUrl(workspaceId, producerId);
        String authUrl = connectResp.authorizationUrl();
        String state = authUrl.substring(authUrl.indexOf("state=") + 6);
        oauthService.handleCallback("good-code-123", state);
    }

    @Test
    void RF08_DISABLED_KEEPS_REJECTION() {
        youtubeProperties.setEnabled(false);

        PublishRequest request = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Video Title",
            "Video Description"
        );

        assertThatThrownBy(() -> publishService.publish(projectId, request, producerId))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo("PUBLISH_TARGET_UNAVAILABLE");
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            });
    }

    @Test
    void RF08_CONNECT_STATE_SIGNED() {
        // 1. Invalid state (tampered) fails
        assertThatThrownBy(() -> oauthService.handleCallback("good-code", "invalid-state-token"))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo("INVALID_STATE");
            });

        // 2. Valid state succeeds and encrypts token in database
        ConnectResponse connectResp = oauthService.buildAuthorizationUrl(workspaceId, producerId);
        String authUrl = connectResp.authorizationUrl();
        String state = authUrl.substring(authUrl.indexOf("state=") + 6);

        String redirectUrl = oauthService.handleCallback("code-xyz", state);
        assertThat(redirectUrl).contains(workspaceId);

        // Verify DB
        Optional<YoutubeChannelConnection> connOpt = connectionRepository
            .findFirstByWorkspaceIdAndRevokedAtIsNullOrderByConnectedAtDesc(workspaceId);
        assertThat(connOpt).isPresent();
        YoutubeChannelConnection conn = connOpt.get();

        assertThat(conn.getChannelId()).isEqualTo("UC_TEST_CHANNEL_123");
        assertThat(conn.getChannelTitle()).isEqualTo("STEMotion Educational Channel");
        // Verify ciphertext is NOT plaintext
        assertThat(conn.getRefreshTokenCiphertext()).doesNotContain("mock-refresh-token-xyz");
        // Verify it decrypts cleanly to original token
        assertThat(encryptionService.decrypt(conn.getRefreshTokenCiphertext())).isEqualTo("mock-refresh-token-xyz");
    }

    @Test
    void RF08_STATUS_NEVER_RETURNS_TOKEN() {
        connectWorkspaceToYoutube();

        ConnectionStatusResponse status = oauthService.getConnectionStatus(workspaceId);
        assertThat(status.connected()).isTrue();
        assertThat(status.channelId()).isEqualTo("UC_TEST_CHANNEL_123");
        assertThat(status.channelTitle()).isEqualTo("STEMotion Educational Channel");
        assertThat(status.connectedBy()).isEqualTo(producerId);

        // Verify DTO does not have token fields and toString does not reveal secrets
        String json = status.toString();
        assertThat(json).doesNotContain("mock-refresh-token");
        assertThat(json).doesNotContain("mock-access-token");
    }

    @Test
    void RF08_PUBLISH_NOT_CONNECTED_409() {
        // Workspace not connected yet
        PublishRequest request = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Newton Laws",
            "Physics lesson"
        );

        assertThatThrownBy(() -> publishService.publish(projectId, request, producerId))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo("YOUTUBE_NOT_CONNECTED");
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    @Test
    void RF08_UPLOAD_HAPPY_PATH() throws Exception {
        connectWorkspaceToYoutube();

        PublishRequest request = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Newton Laws Lesson",
            "Comprehensive physics guide",
            null, null, null,
            List.of("Physics", "Science", "Grade10"),
            "private",
            false
        );

        PublishResponse response = publishService.publish(projectId, request, producerId);

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.uploadStatus()).isEqualTo("PENDING");

        // Wait up to 3 seconds for async upload worker to complete
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            Publication pub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElse(null);
            if (pub != null && "UPLOADED".equals(pub.getUploadStatus())) {
                break;
            }
        }

        Publication finalPub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElseThrow();
        assertThat(finalPub.getUploadStatus()).isEqualTo("UPLOADED");
        assertThat(finalPub.getExternalId()).isEqualTo("yt_vid_test_xyz789");
        assertThat(finalPub.getExternalUrl()).isEqualTo("https://www.youtube.com/watch?v=yt_vid_test_xyz789");

        // Verify metadata sent to Google
        assertThat(lastInitiatedMetadata).contains("\"title\":\"Newton Laws Lesson\"");
        assertThat(lastInitiatedMetadata).contains("\"categoryId\":\"27\"");
        assertThat(lastInitiatedMetadata).contains("\"privacyStatus\":\"private\"");
        assertThat(lastInitiatedMetadata).contains("Nội dung minh hoạ, cần giáo viên kiểm tra");

        // Verify GET publications
        List<PublishResponse> history = publishService.findByProject(projectId);
        assertThat(history).isNotEmpty();
        PublishResponse first = history.get(0);
        assertThat(first.uploadStatus()).isEqualTo("UPLOADED");
        assertThat(first.externalId()).isEqualTo("yt_vid_test_xyz789");
    }

    @Test
    void RF08_RETRY_5XX_THEN_SUCCESS() throws Exception {
        connectWorkspaceToYoutube();
        simulate5xxThenSuccess = true;

        PublishRequest request = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Transient 5xx Test",
            "Testing retry"
        );

        publishService.publish(projectId, request, producerId);

        for (int i = 0; i < 40; i++) {
            Thread.sleep(100);
            Publication pub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElse(null);
            if (pub != null && "UPLOADED".equals(pub.getUploadStatus())) {
                break;
            }
        }

        Publication finalPub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElseThrow();
        assertThat(finalPub.getUploadStatus()).isEqualTo("UPLOADED");
        assertThat(finalPub.getExternalId()).isEqualTo("yt_vid_test_xyz789");
        // Check that retry happened
        assertThat(initUploadAttempts.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void RF08_QUOTA_NO_RETRY() throws Exception {
        connectWorkspaceToYoutube();
        simulateQuotaExceeded = true;

        PublishRequest request = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Quota Test",
            "Testing quota error"
        );

        publishService.publish(projectId, request, producerId);

        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            Publication pub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElse(null);
            if (pub != null && "FAILED".equals(pub.getUploadStatus())) {
                break;
            }
        }

        Publication finalPub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElseThrow();
        assertThat(finalPub.getUploadStatus()).isEqualTo("FAILED");
        assertThat(finalPub.getUploadError()).contains("quotaExceeded");
        // No retry for quota exceeded!
        assertThat(initUploadAttempts.get()).isEqualTo(1);
    }

    @Test
    void RF08_REVOKED_401_FAILED() throws Exception {
        connectWorkspaceToYoutube();
        simulateRevokedToken = true;

        PublishRequest request = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Revoked Token Test",
            "Testing 401"
        );

        publishService.publish(projectId, request, producerId);

        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            Publication pub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElse(null);
            if (pub != null && "FAILED".equals(pub.getUploadStatus())) {
                break;
            }
        }

        Publication finalPub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElseThrow();
        assertThat(finalPub.getUploadStatus()).isEqualTo("FAILED");
        assertThat(finalPub.getUploadError()).contains("401");
        // Did not initiate upload since token was revoked
        assertThat(initUploadAttempts.get()).isEqualTo(0);
    }

    @Test
    void RF08_STEM_FINGERPRINT_GATE_STILL_APPLIES() {
        connectWorkspaceToYoutube();

        // Configure workspace in STRICT mode
        jdbc.update("insert into stem_settings(workspace_id,revision,mode,policy_ids,default_policy_id,legacy_modes) values(?,?,?,?,?,'[]'::jsonb) on conflict(workspace_id) do update set mode=excluded.mode",
                workspaceId, 1, "STRICT", "[\"00000000-0000-0000-0000-000000000001\"]", null);

        // Insert brief on version so STEM fingerprint check triggers
        jdbc.update("insert into stem_briefs(id, version_id, revision, status, readiness, payload) values(?, ?, 1, 'DRAFT', 'INCOMPLETE', '{}'::jsonb)", UUID.randomUUID().toString(), versionId);

        PublishRequest request = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Gate Test",
            "Testing STEM gate before publication"
        );

        // When script has not been approved with matching STEM fingerprint in STRICT mode, publish is blocked
        assertThatThrownBy(() -> publishService.publish(projectId, request, producerId))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            });

        // Ensure no publication was created
        assertThat(publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE)).isEmpty();
    }

    @Test
    void RF08_REPUBLISH_AFTER_FAILED_REUSES_ROW() throws Exception {
        connectWorkspaceToYoutube();
        simulateQuotaExceeded = true;

        PublishRequest request1 = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Failed First Try",
            "Will fail due to quota"
        );

        publishService.publish(projectId, request1, producerId);

        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            Publication p = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElse(null);
            if (p != null && "FAILED".equals(p.getUploadStatus())) break;
        }

        Publication failedPub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElseThrow();
        String initialPubId = failedPub.getId();
        assertThat(failedPub.getUploadStatus()).isEqualTo("FAILED");

        // Now fix the quota and republish
        simulateQuotaExceeded = false;
        PublishRequest request2 = new PublishRequest(
            List.of(PublishTarget.YOUTUBE),
            "Retry After Failure",
            "Should succeed and reuse row"
        );

        PublishResponse resp2 = publishService.publish(projectId, request2, producerId);
        assertThat(resp2.uploadStatus()).isEqualTo("PENDING");

        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            Publication p = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElse(null);
            if (p != null && "UPLOADED".equals(p.getUploadStatus())) break;
        }

        Publication recoveredPub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE).orElseThrow();
        // Verifies that the row was reused!
        assertThat(recoveredPub.getId()).isEqualTo(initialPubId);
        assertThat(recoveredPub.getUploadStatus()).isEqualTo("UPLOADED");
        assertThat(recoveredPub.getExternalId()).isEqualTo("yt_vid_test_xyz789");
    }
}
