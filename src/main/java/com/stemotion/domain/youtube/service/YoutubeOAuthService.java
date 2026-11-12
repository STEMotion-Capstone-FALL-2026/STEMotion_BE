package com.stemotion.domain.youtube.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.youtube.config.YoutubeProperties;
import com.stemotion.domain.youtube.dto.YoutubeDtos.ConnectResponse;
import com.stemotion.domain.youtube.dto.YoutubeDtos.ConnectionStatusResponse;
import com.stemotion.domain.youtube.entity.YoutubeChannelConnection;
import com.stemotion.domain.youtube.repository.YoutubeChannelConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeOAuthService {

    private final YoutubeProperties properties;
    private final YoutubeStateService stateService;
    private final TokenEncryptionService encryptionService;
    private final YoutubeChannelConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void checkEnabled() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PUBLISH_TARGET_UNAVAILABLE", "YouTube OAuth publishing is not configured yet");
        }
    }

    public ConnectResponse buildAuthorizationUrl(String workspaceId, String userId) {
        checkEnabled();
        String state = stateService.generateState(workspaceId, userId);

        String scopes = "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly";
        String authUrl = properties.getAuthUrl() + "?"
                + "client_id=" + urlEncode(properties.getClientId())
                + "&redirect_uri=" + urlEncode(properties.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + urlEncode(scopes)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + urlEncode(state);

        return new ConnectResponse(authUrl);
    }

    @Transactional
    public String handleCallback(String code, String state) {
        checkEnabled();
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_CODE", "Authorization code is required");
        }

        YoutubeStateService.StatePayload payload = stateService.validateAndExtract(state);

        // Exchange code for tokens
        String requestBody = "client_id=" + urlEncode(properties.getClientId())
                + "&client_secret=" + urlEncode(properties.getClientSecret())
                + "&code=" + urlEncode(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + urlEncode(properties.getRedirectUri());

        String rawRefreshToken = null;
        String rawAccessToken = null;
        String grantedScopes = "";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getTokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OAuth token exchange failed with status {}", response.statusCode());
                throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_EXCHANGE_FAILED", "Failed to exchange authorization code");
            }

            JsonNode tokenJson = objectMapper.readTree(response.body());
            if (tokenJson.hasNonNull("refresh_token")) {
                rawRefreshToken = tokenJson.get("refresh_token").asText();
            }
            if (tokenJson.hasNonNull("access_token")) {
                rawAccessToken = tokenJson.get("access_token").asText();
            }
            if (tokenJson.hasNonNull("scope")) {
                grantedScopes = tokenJson.get("scope").asText();
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during OAuth token exchange: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_EXCHANGE_FAILED", "OAuth token exchange error");
        }

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            // Check if we already have an existing refresh token for this workspace
            Optional<YoutubeChannelConnection> existing = connectionRepository
                    .findFirstByWorkspaceIdAndRevokedAtIsNullOrderByConnectedAtDesc(payload.workspaceId());
            if (existing.isPresent()) {
                rawRefreshToken = encryptionService.decrypt(existing.get().getRefreshTokenCiphertext());
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_REFRESH_TOKEN",
                        "Google did not provide a refresh token; ensure prompt=consent is used");
            }
        }

        // Fetch channel info
        String channelId = "unknown";
        String channelTitle = "YouTube Channel";
        try {
            HttpRequest channelReq = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getApiBaseUrl() + "/youtube/v3/channels?part=snippet&mine=true"))
                    .header("Authorization", "Bearer " + rawAccessToken)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> channelResp = httpClient.send(channelReq, HttpResponse.BodyHandlers.ofString());
            if (channelResp.statusCode() == 200) {
                JsonNode chJson = objectMapper.readTree(channelResp.body());
                JsonNode items = chJson.get("items");
                if (items != null && items.isArray() && !items.isEmpty()) {
                    JsonNode first = items.get(0);
                    channelId = first.path("id").asText(channelId);
                    channelTitle = first.path("snippet").path("title").asText(channelTitle);
                }
            } else {
                log.warn("Failed to fetch YouTube channel details, status: {}", channelResp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Could not retrieve YouTube channel info: {}", e.getMessage());
        }

        // Encrypt refresh token
        String encryptedRefreshToken = encryptionService.encrypt(rawRefreshToken);

        // Revoke older active connections for this workspace
        List<YoutubeChannelConnection> currentConnections = connectionRepository.findByWorkspaceId(payload.workspaceId());
        for (YoutubeChannelConnection conn : currentConnections) {
            if (conn.getRevokedAt() == null) {
                conn.setRevokedAt(LocalDateTime.now());
                connectionRepository.save(conn);
            }
        }

        // Save new connection
        YoutubeChannelConnection newConn = YoutubeChannelConnection.builder()
                .workspaceId(payload.workspaceId())
                .channelId(channelId)
                .channelTitle(channelTitle)
                .refreshTokenCiphertext(encryptedRefreshToken)
                .scopes(grantedScopes)
                .connectedBy(payload.userId())
                .connectedAt(LocalDateTime.now())
                .build();
        connectionRepository.save(newConn);

        return String.format(properties.getReturnUrl(), payload.workspaceId());
    }

    @Transactional(readOnly = true)
    public ConnectionStatusResponse getConnectionStatus(String workspaceId) {
        checkEnabled();
        Optional<YoutubeChannelConnection> conn = connectionRepository
                .findFirstByWorkspaceIdAndRevokedAtIsNullOrderByConnectedAtDesc(workspaceId);
        return ConnectionStatusResponse.from(conn.orElse(null));
    }

    @Transactional
    public void revokeConnection(String workspaceId, String userId) {
        checkEnabled();
        Optional<YoutubeChannelConnection> connOpt = connectionRepository
                .findFirstByWorkspaceIdAndRevokedAtIsNullOrderByConnectedAtDesc(workspaceId);
        if (connOpt.isEmpty()) {
            return;
        }
        YoutubeChannelConnection conn = connOpt.get();
        String rawRefreshToken = null;
        try {
            rawRefreshToken = encryptionService.decrypt(conn.getRefreshTokenCiphertext());
        } catch (Exception e) {
            log.warn("Failed to decrypt token during revocation: {}", e.getMessage());
        }

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            try {
                HttpRequest revokeReq = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getRevokeUrl() + "?token=" + urlEncode(rawRefreshToken)))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                httpClient.send(revokeReq, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                log.warn("Failed to call Google revoke endpoint: {}", e.getMessage());
            }
        }

        conn.setRevokedAt(LocalDateTime.now());
        connectionRepository.save(conn);
    }

    private String urlEncode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
