package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Checks a Google Sign-In ID token (proposal: "JWT sessions and Google OAuth
 * 2.0"). The token is sent to Google's tokeninfo endpoint, which validates its
 * signature; the audience, issuer, expiry and verified email are then checked
 * here. No Google account is ever turned into a STEMotion account: the caller
 * only learns which verified email signed in.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    /** The identity Google vouches for. */
    public record GoogleIdentity(String subject, String email) {}

    private final IntegrationProperties properties;
    private final RestClient client;

    public GoogleTokenVerifier(IntegrationProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl("https://oauth2.googleapis.com").requestFactory(factory).build();
    }

    public boolean isConfigured() {
        String clientId = properties.getGoogle().getClientId();
        return clientId != null && !clientId.isBlank();
    }

    public GoogleIdentity verify(String idToken) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_LOGIN_DISABLED",
                "Đăng nhập Google chưa được cấu hình trên máy chủ.");
        }
        JsonNode info;
        try {
            info = client.get().uri(b -> b.path("/tokeninfo").queryParam("id_token", idToken).build())
                .retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            // An invalid or expired token is answered with HTTP 400 by Google.
            log.info("Google rejected an ID token: {}", ex.getMessage());
            throw invalid();
        }
        return check(info, properties.getGoogle().getClientId(), Instant.now());
    }

    /** The claim checks, separate from the HTTP call so they can be tested. */
    static GoogleIdentity check(JsonNode info, String clientId, Instant now) {
        if (info == null) throw invalid();
        if (!clientId.equals(info.path("aud").asText())) throw invalid();
        if (!ISSUERS.contains(info.path("iss").asText())) throw invalid();
        long exp = info.path("exp").asLong(0);
        if (exp <= now.getEpochSecond()) throw invalid();
        String emailVerified = info.path("email_verified").asText();
        String email = info.path("email").asText("");
        if (!"true".equalsIgnoreCase(emailVerified) || email.isBlank()) throw invalid();
        String subject = info.path("sub").asText("");
        if (subject.isBlank()) throw invalid();
        return new GoogleIdentity(subject, email.toLowerCase());
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("Đăng nhập Google không hợp lệ hoặc đã hết hạn.");
    }
}
