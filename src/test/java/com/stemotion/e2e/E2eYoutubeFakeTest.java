package com.stemotion.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.SimpleCommandLinePropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** The fake Google endpoints answer the way domain.youtube expects, and the E2E opt-in wires them. */
class E2eYoutubeFakeTest {

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private E2eYoutubeFake fake;
    private String base;

    @BeforeEach
    void start() throws Exception {
        fake = E2eYoutubeFake.startHttpOnly(0);
        base = "http://127.0.0.1:" + fake.boundHttpPort();
    }

    @AfterEach
    void stop() {
        fake.stop();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder at(String path) {
        return HttpRequest.newBuilder(URI.create(base + path));
    }

    @Test
    void consentReturnsToTheLocalCallbackWithCodeAndState() throws Exception {
        var response = send(at("/o/oauth2/v2/auth?client_id=x&redirect_uri=" + java.net.URLEncoder.encode("http://127.0.0.1:18080/api/v1/youtube/oauth/callback", "UTF-8") + "&state=abc.def").GET().build());
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValue("http://127.0.0.1:18080/api/v1/youtube/oauth/callback?code=e2e-auth-code&state=abc.def");

        var foreign = send(at("/o/oauth2/v2/auth?redirect_uri=" + java.net.URLEncoder.encode("https://evil.example/cb", "UTF-8")).GET().build());
        assertThat(foreign.statusCode()).isEqualTo(400);
    }

    @Test
    void tokenChannelAndResumableUploadFollowTheGoogleShapes() throws Exception {
        var token = send(at("/token").header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&code=e2e-auth-code")).build());
        assertThat(token.body()).contains("\"refresh_token\":\"e2e-refresh\"");
        var refresh = send(at("/token").POST(HttpRequest.BodyPublishers.ofString("grant_type=refresh_token&refresh_token=e2e-refresh")).build());
        assertThat(refresh.body()).contains("\"access_token\":\"e2e-access-2\"");
        var bad = send(at("/token").POST(HttpRequest.BodyPublishers.ofString("grant_type=refresh_token&refresh_token=other")).build());
        assertThat(bad.statusCode()).isEqualTo(400);

        var channels = send(at("/youtube/v3/channels?part=snippet&mine=true").header("Authorization", "Bearer e2e-access-2").GET().build());
        assertThat(channels.body()).contains(E2eYoutubeFake.CHANNEL_ID).contains(E2eYoutubeFake.CHANNEL_TITLE);
        assertThat(send(at("/youtube/v3/channels").GET().build()).statusCode()).isEqualTo(401);

        String location = openSession("{\"snippet\":{\"title\":\"Đèn dung nham\"},\"status\":{\"privacyStatus\":\"private\"}}");
        var put = send(HttpRequest.newBuilder(URI.create(location)).PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3})).build());
        assertThat(put.statusCode()).isEqualTo(200);
        assertThat(put.body()).contains("\"id\":\"e2eVideo");
        assertThat(send(at("/__e2e/uploads").GET().build()).body())
            .contains("\"title\":\"Đèn dung nham\"").contains("\"privacyStatus\":\"private\"").contains("\"bytes\":3");
    }

    @Test
    void quotaMarkerFailsTheFirstUploadOnlyAndEmptyBodiesAreRefused() throws Exception {
        String metadata = "{\"snippet\":{\"title\":\"Clip " + E2eYoutubeFake.QUOTA_ONCE_MARKER + "\"},\"status\":{\"privacyStatus\":\"private\"}}";
        var first = send(HttpRequest.newBuilder(URI.create(openSession(metadata))).PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1})).build());
        assertThat(first.statusCode()).isEqualTo(403);
        assertThat(first.body()).contains("quotaExceeded");
        var second = send(HttpRequest.newBuilder(URI.create(openSession(metadata))).PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1})).build());
        assertThat(second.statusCode()).isEqualTo(200);
        var empty = send(HttpRequest.newBuilder(URI.create(openSession(metadata))).PUT(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(empty.statusCode()).isEqualTo(400);
    }

    private String openSession(String metadata) throws Exception {
        var init = send(at("/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .header("Authorization", "Bearer e2e-access-2").POST(HttpRequest.BodyPublishers.ofString(metadata)).build());
        assertThat(init.statusCode()).isEqualTo(200);
        return init.headers().firstValue("Location").orElseThrow();
    }

    @Test
    void optInPointsEveryYoutubeUrlAtTheFakeAndReturnsToTheE2eWebPort() {
        System.setProperty("E2E_YOUTUBE", "1");
        try {
            var source = new SimpleCommandLinePropertySource(E2eServer.launchArguments());
            assertThat(source.getProperty("app.youtube.enabled")).isEqualTo("true");
            assertThat(source.getProperty("app.youtube.token-url")).isEqualTo("http://127.0.0.1:18090/token");
            assertThat(source.getProperty("app.youtube.upload-base-url")).isEqualTo("http://127.0.0.1:18090/upload/youtube/v3/videos");
            assertThat(source.getProperty("app.youtube.redirect-uri")).isEqualTo("http://127.0.0.1:18080/api/v1/youtube/oauth/callback");
            assertThat(source.getProperty("app.youtube.return-url")).isEqualTo("http://127.0.0.1:5174/workspaces/%s/settings");
            assertThat(E2eYoutubeFake.videoBaseUrl()).startsWith("https://127.0.0.1:18443/");
        } finally {
            System.clearProperty("E2E_YOUTUBE");
        }
        var off = new SimpleCommandLinePropertySource(E2eServer.launchArguments());
        assertThat(off.containsProperty("app.youtube.enabled")).isFalse();
    }
}
