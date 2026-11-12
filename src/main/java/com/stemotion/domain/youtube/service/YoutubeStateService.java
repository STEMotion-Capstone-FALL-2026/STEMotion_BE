package com.stemotion.domain.youtube.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.youtube.config.YoutubeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class YoutubeStateService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long STATE_TTL_SECONDS = 600; // 10 minutes

    private final YoutubeProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public record StatePayload(String workspaceId, String userId, long timestamp, String nonce) {}

    private byte[] getHmacKey() {
        String key = properties.getTokenKey();
        if (key == null || key.isBlank()) {
            key = properties.getClientSecret();
        }
        if (key == null || key.isBlank()) {
            key = "fallback-test-hmac-key-for-youtube-state";
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to digest state key", e);
        }
    }

    public String generateState(String workspaceId, String userId) {
        long timestamp = Instant.now().getEpochSecond();
        byte[] nonceBytes = new byte[8];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        String data = workspaceId + ":" + userId + ":" + timestamp + ":" + nonce;
        String signature = sign(data);
        String combined = data + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }

    public StatePayload validateAndExtract(String stateToken) {
        if (stateToken == null || stateToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "OAuth state token is required");
        }
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(stateToken);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 5) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Malformed OAuth state token");
            }
            String workspaceId = parts[0];
            String userId = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            String nonce = parts[3];
            String signature = parts[4];

            String data = workspaceId + ":" + userId + ":" + timestamp + ":" + nonce;
            String expectedSignature = sign(data);

            if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "OAuth state signature mismatch");
            }

            long now = Instant.now().getEpochSecond();
            if (now - timestamp > STATE_TTL_SECONDS || timestamp > now + 60) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "STATE_EXPIRED", "OAuth state has expired");
            }

            return new StatePayload(workspaceId, userId, timestamp, nonce);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Invalid OAuth state token: " + e.getMessage());
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(getHmacKey(), HMAC_SHA256));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}
