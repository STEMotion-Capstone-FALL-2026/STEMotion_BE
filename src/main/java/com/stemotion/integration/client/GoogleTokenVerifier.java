package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.*;

/** Verifies Google signatures using cached, rotating public keys. Never logs tokens. */
@Component
public class GoogleTokenVerifier {
    public record GoogleIdentity(String subject, String email, String name, String nonce) {
        public GoogleIdentity(String subject, String email) { this(subject, email, "", ""); }
    }
    private final IntegrationProperties properties;
    private final JwtDecoder decoder;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleTokenVerifier(IntegrationProperties properties) {
        this(properties, defaultDecoder());
    }
    GoogleTokenVerifier(IntegrationProperties properties, JwtDecoder decoder) {
        this.properties = properties;
        this.decoder = decoder;
    }
    private static JwtDecoder defaultDecoder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(10000);
        return NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .restOperations(new RestTemplate(factory)).build();
    }
    public boolean isConfigured() {
        String id = properties.getGoogle().getClientId();
        return id != null && !id.isBlank();
    }
    public GoogleIdentity verify(String token) {
        if (!isConfigured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_LOGIN_DISABLED", "Đăng nhập Google chưa được cấu hình.");
        try {
            var jwt = decoder.decode(token);
            String issuer = jwt.getClaimAsString("iss");
            if (jwt.getAudience() == null || !jwt.getAudience().contains(properties.getGoogle().getClientId())
                    || issuer == null || !Set.of("accounts.google.com", "https://accounts.google.com").contains(issuer)
                    || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())
                    || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) throw invalid();
            String email = jwt.getClaimAsString("email");
            if (jwt.getSubject() == null || jwt.getSubject().isBlank() || email == null || email.isBlank()) throw invalid();
            return new GoogleIdentity(jwt.getSubject(), email.toLowerCase(Locale.ROOT),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("nonce"));
        } catch (BadJwtException | IllegalArgumentException ex) { throw invalid(); }
        catch (JwtException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_VERIFICATION_UNAVAILABLE", "Không thể xác minh Google. Vui lòng thử lại.");
        }
    }
    /** Claim-only helper for isolated tests, not a production verification entry. */
    static GoogleIdentity check(JsonNode info, String clientId, Instant now) {
        if (info == null || !clientId.equals(info.path("aud").asText())
            || !Set.of("accounts.google.com", "https://accounts.google.com").contains(info.path("iss").asText())
            || info.path("exp").asLong(0) <= now.getEpochSecond()
            || !"true".equalsIgnoreCase(info.path("email_verified").asText())
            || info.path("sub").asText().isBlank() || info.path("email").asText().isBlank()) throw invalid();
        return new GoogleIdentity(info.path("sub").asText(), info.path("email").asText().toLowerCase(Locale.ROOT));
    }
    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_INVALID", "Đăng nhập Google không hợp lệ hoặc đã hết hạn.");
    }
}
