package com.stemotion.e2e;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stand-in for Google's OAuth, YouTube Data API and resumable upload endpoints, for the browser
 * YouTube test (E2E_YOUTUBE=1). Test scope only: the real services in domain.youtube run unchanged
 * and only their base URLs point here. Nothing leaves 127.0.0.1.
 *
 * Clips must be served over HTTPS (an LMS embed refuses anything else), so a second HTTPS listener
 * serves the fake MP4 files, with a self-signed certificate made by keytool at start and trusted by
 * this JVM's default TLS context. No key material is stored in the repository.
 */
final class E2eYoutubeFake {

    static final String CHANNEL_ID = "UC_E2E_FAKE_CHANNEL";
    static final String CHANNEL_TITLE = "Kênh E2E STEMotion";
    /** A clip title containing this marker gets quotaExceeded on its first upload, then succeeds. */
    static final String QUOTA_ONCE_MARKER = "YT-QUOTA-ONCE";
    /** Throwaway keystore secret, new on every start; the keystore lives in a temp directory. */
    private static final String STORE_SECRET = java.util.UUID.randomUUID().toString();

    private final HttpServer http;
    private final HttpsServer https;
    private final AtomicInteger sessions = new AtomicInteger();
    private final Map<String, String> sessionMetadata = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> uploadsByTitle = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> completed = new ArrayList<>();

    private E2eYoutubeFake(HttpServer http, HttpsServer https) {
        this.http = http;
        this.https = https;
    }

    static boolean enabled() {
        return "1".equals(E2eServer.env("E2E_YOUTUBE", "0"));
    }

    static int httpPort() {
        return Integer.parseInt(E2eServer.env("E2E_YOUTUBE_PORT", "18090"));
    }

    static int videoPort() {
        return Integer.parseInt(E2eServer.env("E2E_YOUTUBE_VIDEO_PORT", "18443"));
    }

    static String baseUrl(int port) {
        return "http://127.0.0.1:" + port;
    }

    /** Where the fake render worker puts finished clips while the YouTube fake runs. */
    static String videoBaseUrl() {
        return "https://127.0.0.1:" + videoPort() + "/videos/";
    }

    /** Spring arguments that turn YouTube on and point every Google URL at this fake. */
    static List<String> arguments(int httpPort, String apiPort, String webPort) {
        String base = baseUrl(httpPort);
        return List.of(
            "--app.youtube.enabled=true",
            "--app.youtube.client-id=e2e-youtube-client.apps.googleusercontent.com",
            "--app.youtube.client-secret=e2e-only-client-secret",
            "--app.youtube.token-key=e2e-only-youtube-token-key-not-used-anywhere-else",
            "--app.youtube.redirect-uri=http://127.0.0.1:" + apiPort + "/api/v1/youtube/oauth/callback",
            "--app.youtube.return-url=http://127.0.0.1:" + webPort + "/workspaces/%s/settings",
            "--app.youtube.auth-url=" + base + "/o/oauth2/v2/auth",
            "--app.youtube.token-url=" + base + "/token",
            "--app.youtube.revoke-url=" + base + "/revoke",
            "--app.youtube.api-base-url=" + base,
            "--app.youtube.upload-base-url=" + base + "/upload/youtube/v3/videos");
    }

    /** Makes the certificate, trusts it for this JVM, and starts both listeners. */
    static E2eYoutubeFake start(int httpPort, int videoPort) throws Exception {
        Path dir = Files.createTempDirectory("stemotion-e2e-youtube");
        Path keystore = dir.resolve("fake-google.p12");
        // keytool reads the random keystore secret from a temp file, never from the command line.
        Path secretFile = dir.resolve("keystore.secret");
        Files.writeString(secretFile, STORE_SECRET, StandardCharsets.UTF_8);
        String fromFile = secretFile.toString();
        keytool("-genkeypair", "-alias", "fake-google", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3",
            "-dname", "CN=127.0.0.1", "-ext", "SAN=ip:127.0.0.1,dns:localhost",
            "-keystore", keystore.toString(), "-storetype", "PKCS12", "-storepass:file", fromFile, "-keypass:file", fromFile, "-noprompt");
        KeyStore keys = load(keystore);
        // This JVM's default TLS context trusts the fake certificate (and nothing else: the E2E run makes
        // no other HTTPS call). The API's HttpClient picks it up when it fetches the clip.
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        trust.setCertificateEntry("fake-google", keys.getCertificate("fake-google"));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        SSLContext client = SSLContext.getInstance("TLS");
        client.init(null, tmf.getTrustManagers(), null);
        SSLContext.setDefault(client);

        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 0);
        HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", videoPort), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(serverContext(keys)));
        E2eYoutubeFake fake = new E2eYoutubeFake(http, https);
        fake.routes();
        http.setExecutor(Executors.newCachedThreadPool());
        https.setExecutor(Executors.newCachedThreadPool());
        http.start();
        https.start();
        return fake;
    }

    /** For tests that run the fake in plain HTTP only. */
    static E2eYoutubeFake startHttpOnly(int httpPort) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 0);
        E2eYoutubeFake fake = new E2eYoutubeFake(http, null);
        fake.routes();
        http.start();
        return fake;
    }

    int boundHttpPort() {
        return http.getAddress().getPort();
    }

    void stop() {
        http.stop(0);
        if (https != null) https.stop(0);
    }

    private void routes() {
        // Consent screen: answers at once, as if the user approved, and returns to the API callback.
        http.createContext("/o/oauth2/v2/auth", exchange -> {
            Map<String, String> query = query(exchange.getRequestURI());
            String redirect = query.get("redirect_uri");
            if (redirect == null || !redirect.startsWith("http://127.0.0.1:")) {
                send(exchange, 400, "text/plain", "redirect_uri must point at the local E2E API");
                return;
            }
            String location = redirect + "?code=e2e-auth-code&state=" + URLEncoder.encode(query.getOrDefault("state", ""), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Location", location);
            send(exchange, 302, "text/plain", "");
        });
        http.createContext("/token", exchange -> {
            Map<String, String> form = form(exchange);
            if ("authorization_code".equals(form.get("grant_type")) && "e2e-auth-code".equals(form.get("code"))) {
                send(exchange, 200, "application/json", "{\"access_token\":\"e2e-access-1\",\"refresh_token\":\"e2e-refresh\",\"expires_in\":3600,"
                    + "\"scope\":\"https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly\",\"token_type\":\"Bearer\"}");
            } else if ("refresh_token".equals(form.get("grant_type")) && "e2e-refresh".equals(form.get("refresh_token"))) {
                send(exchange, 200, "application/json", "{\"access_token\":\"e2e-access-2\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
            } else {
                send(exchange, 400, "application/json", "{\"error\":\"invalid_grant\"}");
            }
        });
        http.createContext("/revoke", exchange -> send(exchange, 200, "application/json", "{}"));
        http.createContext("/youtube/v3/channels", exchange -> {
            if (!bearer(exchange)) { send(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}"); return; }
            send(exchange, 200, "application/json", "{\"items\":[{\"id\":\"" + CHANNEL_ID + "\",\"snippet\":{\"title\":\"" + CHANNEL_TITLE + "\"}}]}");
        });
        // Resumable upload: POST opens a session (metadata), PUT to the session sends the bytes.
        http.createContext("/upload/youtube/v3/videos", exchange -> {
            if (!bearer(exchange) || !"POST".equals(exchange.getRequestMethod())) { send(exchange, 401, "application/json", "{}"); return; }
            String id = "session-" + sessions.incrementAndGet();
            sessionMetadata.put(id, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Location", baseUrl(boundHttpPort()) + "/upload/session/" + id);
            send(exchange, 200, "application/json", "{}");
        });
        http.createContext("/upload/session/", exchange -> {
            String id = exchange.getRequestURI().getPath().substring("/upload/session/".length());
            String metadata = sessionMetadata.get(id);
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            if (metadata == null || bytes.length == 0) { send(exchange, 400, "application/json", "{\"error\":{\"message\":\"empty upload\"}}"); return; }
            String title = jsonField(metadata, "title");
            int attempt = uploadsByTitle.computeIfAbsent(title, t -> new AtomicInteger()).incrementAndGet();
            if (title.contains(QUOTA_ONCE_MARKER) && attempt == 1) {
                send(exchange, 403, "application/json", "{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}],\"message\":\"quotaExceeded\"}}");
                return;
            }
            sleep(1200); // long enough for the browser to see UPLOADING
            String videoId = "e2eVideo" + id.replace("session-", "");
            synchronized (completed) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("videoId", videoId);
                row.put("title", title);
                row.put("privacyStatus", jsonField(metadata, "privacyStatus"));
                row.put("bytes", bytes.length);
                completed.add(row);
            }
            send(exchange, 200, "application/json", "{\"id\":\"" + videoId + "\",\"status\":{\"uploadStatus\":\"uploaded\"}}");
        });
        // Read by the Playwright spec to check what reached "YouTube".
        http.createContext("/__e2e/uploads", exchange -> {
            StringBuilder json = new StringBuilder("[");
            synchronized (completed) {
                for (int i = 0; i < completed.size(); i++) {
                    Map<String, Object> row = completed.get(i);
                    if (i > 0) json.append(',');
                    json.append("{\"videoId\":\"").append(row.get("videoId")).append("\",\"title\":\"").append(escape(String.valueOf(row.get("title"))))
                        .append("\",\"privacyStatus\":\"").append(row.get("privacyStatus")).append("\",\"bytes\":").append(row.get("bytes")).append('}');
                }
            }
            send(exchange, 200, "application/json", json.append(']').toString());
        });
        if (https != null) {
            https.createContext("/videos/", exchange -> send(exchange, 200, "video/mp4", "E2E-FAKE-MP4-BYTES"));
        }
    }

    private static KeyStore load(Path keystore) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            store.load(in, STORE_SECRET.toCharArray());
        }
        return store;
    }

    private static SSLContext serverContext(KeyStore store) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, STORE_SECRET.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }

    private static void keytool(String... args) throws Exception {
        Path tool = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "keytool.exe" : "keytool");
        List<String> command = new ArrayList<>();
        command.add(tool.toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean bearer(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.startsWith("Bearer e2e-access-");
    }

    private static Map<String, String> query(URI uri) {
        return parse(uri.getRawQuery());
    }

    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        return parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static Map<String, String> parse(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return values;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            values.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    /** Enough JSON for the metadata the API sends: the first "name":"value" string field. */
    static String jsonField(String json, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type + (type.startsWith("video") ? "" : "; charset=utf-8"));
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
