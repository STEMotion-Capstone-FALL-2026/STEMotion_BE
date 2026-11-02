package com.stemotion.domain.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

@Component
public class ConfiguredNotificationSender implements NotificationSender {
    private final EmailProperties properties;
    private final RestOperations transport;

    @org.springframework.beans.factory.annotation.Autowired
    public ConfiguredNotificationSender(EmailProperties properties) {
        this(properties,transport(properties));
    }
    ConfiguredNotificationSender(EmailProperties properties, RestOperations transport) {
        this.properties=properties;this.transport=transport;
    }
    private static RestOperations transport(EmailProperties properties) {
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    @Override
    public String send(String eventId, String recipient, String html) {
        if (!properties.isEnabled()) throw new Failure("EMAIL_DISABLED", false, false, 0);
        if ("local".equals(properties.getProvider())) {
            try {
                var directory = Path.of(properties.getPreviewDirectory()).toAbsolutePath().normalize();
                Files.createDirectories(directory);
                // UUID event names only. Recipient never becomes a filename or public URL.
                java.util.UUID.fromString(eventId);
                Files.writeString(directory.resolve(eventId + ".html"), html, StandardCharsets.UTF_8);
                return "local-preview:" + eventId;
            } catch (java.io.IOException ex) {
                throw new Failure("PREVIEW_WRITE_FAILED", false, false, 0);
            }
        }
        if (!"brevo".equals(properties.getProvider())) throw new Failure("INVALID_PROVIDER", false, false, 0);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", properties.getApiKey());
        var payload = Map.of("sender", Map.of("email", properties.getFromAddress(), "name", properties.getFromName()),
            "to", java.util.List.of(Map.of("email", recipient)),
            "subject", "STEMotion — Bạn đã được cấp quyền đăng nhập bằng Google", "htmlContent", html);
        try {
            var response = transport.postForObject("https://api.brevo.com/v3/smtp/email",
                new HttpEntity<>(payload, headers), Map.class);
            if (response == null || !(response.get("messageId") instanceof String id) || id.length() > 255)
                throw new Failure("PROVIDER_RESPONSE_UNKNOWN", false, true, 0);
            return id;
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            long retryAfter = 0;
            if (ex.getResponseHeaders() != null) {
                String raw = ex.getResponseHeaders().getFirst("Retry-After");
                try { retryAfter = Long.parseLong(raw); }
                catch (Exception ignored) {
                    try { retryAfter = java.time.Duration.between(java.time.Instant.now(),
                        java.time.ZonedDateTime.parse(raw, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds(); }
                    catch (Exception invalid) { /* Default bounded backoff. */ }
                }
            }
            throw new Failure("PROVIDER_HTTP_" + status, status == 429 || status >= 500, false, Math.max(0, retryAfter));
        } catch (ResourceAccessException ex) {
            // A timeout can happen after acceptance; never silently send a duplicate.
            throw new Failure("PROVIDER_OUTCOME_UNKNOWN", false, true, 0);
        } catch (RestClientException ex) {
            throw new Failure("PROVIDER_RESPONSE_UNKNOWN", false, true, 0);
        }
    }
}
