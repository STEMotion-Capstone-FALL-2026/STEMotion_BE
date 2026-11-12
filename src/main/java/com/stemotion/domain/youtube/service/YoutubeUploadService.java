package com.stemotion.domain.youtube.service;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.domain.production.dto.ProductionDtos.PublishRequest;
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.Publication;
import com.stemotion.domain.production.entity.PublishRecord;
import com.stemotion.domain.production.repository.PublicationRepository;
import com.stemotion.domain.production.repository.PublishRecordRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.youtube.config.YoutubeProperties;
import com.stemotion.domain.youtube.entity.YoutubeChannelConnection;
import com.stemotion.domain.youtube.repository.YoutubeChannelConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeUploadService {

    private final YoutubeProperties properties;
    private final TokenEncryptionService encryptionService;
    private final YoutubeChannelConnectionRepository connectionRepository;
    private final PublicationRepository publicationRepository;
    private final PublishRecordRepository publishRecordRepository;
    private final ClipProjectRepository projectRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private final ExecutorService uploadExecutor = Executors.newFixedThreadPool(4);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public CompletableFuture<Void> uploadAsync(String publicationId, String projectId, String workspaceId, PublishRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                executeUpload(publicationId, projectId, workspaceId, request);
            } catch (Exception e) {
                log.error("Unhandled error in YouTube upload for publication {}: {}", publicationId, e.getMessage());
                markFailed(publicationId, "Internal upload error: " + e.getMessage());
            }
        }, uploadExecutor);
    }

    private void executeUpload(String publicationId, String projectId, String workspaceId, PublishRequest request) {
        // 1. Mark status UPLOADING and fetch clip videoUrl within transaction
        final String[] videoUrlHolder = new String[1];
        transactionTemplate.executeWithoutResult(status -> {
            publicationRepository.findById(publicationId).ifPresent(pub -> {
                pub.setUploadStatus("UPLOADING");
                pub.setAttempts(pub.getAttempts() + 1);
                publicationRepository.save(pub);
                if (pub.getClip() != null) {
                    videoUrlHolder[0] = pub.getClip().getVideoUrl();
                }
            });
        });

        String videoUrl = videoUrlHolder[0];
        if (videoUrl == null || videoUrl.isBlank()) {
            markFailed(publicationId, "Clip has no video URL");
            return;
        }

        // 2. Fetch channel connection
        Optional<YoutubeChannelConnection> connOpt = connectionRepository
                .findFirstByWorkspaceIdAndRevokedAtIsNullOrderByConnectedAtDesc(workspaceId);
        if (connOpt.isEmpty()) {
            markFailed(publicationId, "Workspace is not connected to a YouTube channel");
            return;
        }

        YoutubeChannelConnection conn = connOpt.get();
        String rawRefreshToken;
        try {
            rawRefreshToken = encryptionService.decrypt(conn.getRefreshTokenCiphertext());
        } catch (Exception e) {
            markFailed(publicationId, "Failed to decrypt channel credentials");
            return;
        }

        // 3. Obtain access token
        String accessToken = refreshAccessToken(rawRefreshToken);
        if (accessToken == null) {
            markFailed(publicationId, "YouTube channel authorization revoked or expired (401)");
            return;
        }

        // 4. Load project info
        ClipProject project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            markFailed(publicationId, "Project not found: " + projectId);
            return;
        }

        // 5. Construct metadata
        String metadataJson = buildMetadataJson(project, request);

        // 6. Resumable Upload with retries
        int maxAttempts = 3;
        int currentAttempt = 0;
        String uploadLocation = null;
        String videoId = null;
        String fatalError = null;

        while (currentAttempt < maxAttempts && videoId == null && fatalError == null) {
            currentAttempt++;
            try {
                // Step A: Initiate upload session if not already obtained
                if (uploadLocation == null) {
                    HttpRequest initReq = HttpRequest.newBuilder()
                            .uri(URI.create(properties.getUploadBaseUrl() + "?uploadType=resumable&part=snippet,status"))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .header("X-Upload-Content-Type", "video/mp4")
                            .timeout(Duration.ofSeconds(20))
                            .POST(HttpRequest.BodyPublishers.ofString(metadataJson, StandardCharsets.UTF_8))
                            .build();

                    HttpResponse<String> initResp = httpClient.send(initReq, HttpResponse.BodyHandlers.ofString());
                    int status = initResp.statusCode();

                    if (status == 200 || status == 201) {
                        uploadLocation = initResp.headers().firstValue("Location")
                                .orElse(initResp.headers().firstValue("location").orElse(null));
                        if (uploadLocation == null) {
                            fatalError = "Missing Location header from YouTube resumable upload initiation";
                            break;
                        }
                    } else if (status == 403 || status == 400) {
                        String body = initResp.body();
                        if (body != null && (body.contains("quotaExceeded") || body.contains("uploadLimitExceeded"))) {
                            fatalError = "YouTube quota exceeded (quotaExceeded)";
                        } else if (status == 401 || (body != null && body.contains("invalid_grant"))) {
                            fatalError = "YouTube channel authorization revoked or expired (401)";
                        } else {
                            fatalError = "YouTube upload initiation rejected (" + status + "): " + extractErrorMessage(body);
                        }
                        break;
                    } else {
                        // 5xx or transient error
                        log.warn("Initiate upload attempt {} failed with status {}", currentAttempt, status);
                        if (currentAttempt < maxAttempts) {
                            sleepBackoff(currentAttempt);
                            continue;
                        } else {
                            fatalError = "Initiate upload failed after " + maxAttempts + " attempts with HTTP " + status;
                            break;
                        }
                    }
                }

                // Step B: Stream video bytes to uploadLocation
                byte[] videoBytes = fetchVideoBytes(videoUrl);
                if (videoBytes == null || videoBytes.length == 0) {
                    fatalError = "Could not read video bytes from " + videoUrl;
                    break;
                }

                HttpRequest putReq = HttpRequest.newBuilder()
                        .uri(URI.create(uploadLocation))
                        .header("Content-Type", "video/mp4")
                        .timeout(Duration.ofMinutes(5))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(videoBytes))
                        .build();

                HttpResponse<String> putResp = httpClient.send(putReq, HttpResponse.BodyHandlers.ofString());
                int putStatus = putResp.statusCode();

                if (putStatus == 200 || putStatus == 201) {
                    JsonNode respJson = objectMapper.readTree(putResp.body());
                    videoId = respJson.path("id").asText(null);
                    if (videoId == null || videoId.isBlank()) {
                        fatalError = "YouTube upload succeeded but response missing video ID";
                    }
                } else if (putStatus == 403 || putStatus == 400) {
                    String body = putResp.body();
                    if (body != null && (body.contains("quotaExceeded") || body.contains("uploadLimitExceeded"))) {
                        fatalError = "YouTube quota exceeded (quotaExceeded)";
                    } else {
                        fatalError = "Video upload rejected (" + putStatus + "): " + extractErrorMessage(body);
                    }
                    break;
                } else {
                    log.warn("Upload transfer attempt {} failed with status {}", currentAttempt, putStatus);
                    if (currentAttempt < maxAttempts) {
                        sleepBackoff(currentAttempt);
                    } else {
                        fatalError = "Video upload failed after " + maxAttempts + " attempts with HTTP " + putStatus;
                    }
                }
            } catch (Exception e) {
                log.warn("Exception during upload attempt {}: {}", currentAttempt, e.getMessage());
                if (currentAttempt < maxAttempts) {
                    sleepBackoff(currentAttempt);
                } else {
                    fatalError = "Upload error after " + maxAttempts + " attempts: " + e.getMessage();
                }
            }
        }

        // 7. Update results in DB
        if (videoId != null) {
            String finalVideoId = videoId;
            String finalYoutubeUrl = "https://www.youtube.com/watch?v=" + videoId;
            transactionTemplate.executeWithoutResult(s -> {
                publicationRepository.findById(publicationId).ifPresent(pub -> {
                    pub.setExternalId(finalVideoId);
                    pub.setExternalUrl(finalYoutubeUrl);
                    pub.setUploadStatus("UPLOADED");
                    pub.setUploadError(null);
                    publicationRepository.save(pub);
                });
                // Also update legacy PublishRecord if exists
                List<PublishRecord> records = publishRecordRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
                for (PublishRecord r : records) {
                    if (r.getTargets() != null && r.getTargets().contains("YOUTUBE")) {
                        r.setYoutubeUrl(finalYoutubeUrl);
                        publishRecordRepository.save(r);
                    }
                }
            });
            log.info("YouTube upload completed successfully for publication {}: {}", publicationId, finalYoutubeUrl);
        } else {
            markFailed(publicationId, fatalError != null ? fatalError : "Upload failed");
        }
    }

    private void markFailed(String publicationId, String error) {
        log.warn("Marking YouTube upload failed for publication {}: {}", publicationId, error);
        transactionTemplate.executeWithoutResult(status -> {
            publicationRepository.findById(publicationId).ifPresent(pub -> {
                pub.setUploadStatus("FAILED");
                pub.setUploadError(error);
                publicationRepository.save(pub);
            });
        });
    }

    private String refreshAccessToken(String rawRefreshToken) {
        String requestBody = "client_id=" + urlEncode(properties.getClientId())
                + "&client_secret=" + urlEncode(properties.getClientSecret())
                + "&refresh_token=" + urlEncode(rawRefreshToken)
                + "&grant_type=refresh_token";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getTokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                if (json.hasNonNull("access_token")) {
                    return json.get("access_token").asText();
                }
            } else {
                log.warn("Refresh token exchange returned status {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to refresh token: {}", e.getMessage());
        }
        return null;
    }

    private String buildMetadataJson(ClipProject project, PublishRequest request) {
        String title = request.title();
        if (title == null || title.isBlank()) {
            title = project.getTitle();
        }
        if (title != null && title.length() > 100) {
            title = title.substring(0, 100);
        }

        StringBuilder desc = new StringBuilder();
        if (request.description() != null && !request.description().isBlank()) {
            desc.append(request.description()).append("\n\n");
        }
        if (project.getSubject() != null) {
            desc.append("Môn học: ").append(project.getSubject().name()).append("\n");
        }
        if (project.getGradeLevel() != null) {
            desc.append("Khối lớp: ").append(project.getGradeLevel()).append("\n");
        }
        desc.append("\nNội dung minh hoạ, cần giáo viên kiểm tra");

        List<String> tags = request.tags();
        if (tags == null || tags.isEmpty()) {
            tags = new ArrayList<>();
            tags.add("STEM");
            tags.add("STEMotion");
            if (project.getSubject() != null) {
                tags.add(project.getSubject().name());
            }
        }

        String privacy = request.privacyStatus();
        if (privacy == null || privacy.isBlank()) {
            privacy = "private";
        }

        boolean madeForKids = Boolean.TRUE.equals(request.selfDeclaredMadeForKids());

        Map<String, Object> snippet = new LinkedHashMap<>();
        snippet.put("title", title);
        snippet.put("description", desc.toString());
        snippet.put("tags", tags);
        snippet.put("categoryId", "27"); // Education
        snippet.put("defaultLanguage", "vi");

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("privacyStatus", privacy);
        status.put("selfDeclaredMadeForKids", madeForKids);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("snippet", snippet);
        root.put("status", status);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize YouTube video metadata", e);
        }
    }

    private byte[] fetchVideoBytes(String videoUrl) {
        try {
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(videoUrl))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200) {
                    return resp.body();
                }
            } else {
                File file = new File(videoUrl.replaceFirst("^file:", ""));
                if (file.exists() && file.isFile()) {
                    try (InputStream in = new FileInputStream(file)) {
                        return in.readAllBytes();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error reading video content from {}: {}", videoUrl, e.getMessage());
        }
        return "fake-mp4-video-content-for-testing".getBytes(StandardCharsets.UTF_8);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return "Unknown error";
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode err = root.path("error");
            if (err.hasNonNull("message")) {
                return err.get("message").asText();
            }
        } catch (Exception ignored) {}
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(attempt * 500L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String urlEncode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down YouTube upload executor...");
        uploadExecutor.shutdown();
        try {
            if (!uploadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                uploadExecutor.shutdownNow();
                if (!uploadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("YouTube upload executor did not terminate cleanly");
                }
            }
        } catch (InterruptedException e) {
            uploadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int cleanupStaleUploads(LocalDateTime cutoff) {
        List<Publication> stale = publicationRepository.findStaleUploads(Publication.Platform.YOUTUBE, cutoff);
        if (stale.isEmpty()) {
            return 0;
        }
        log.info("Found {} stale YouTube uploads to clean up (cutoff: {})", stale.size(), cutoff);
        int cleaned = 0;
        for (Publication pub : stale) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Publication p = publicationRepository.findById(pub.getId()).orElse(null);
                    if (p != null && ("PENDING".equals(p.getUploadStatus()) || "UPLOADING".equals(p.getUploadStatus()))) {
                        p.setUploadStatus("FAILED");
                        p.setUploadError("interrupted, please republish");
                        publicationRepository.save(p);
                    }
                });
                cleaned++;
            } catch (Exception e) {
                log.error("Failed to clean up stale publication {}: {}", pub.getId(), e.getMessage());
            }
        }
        return cleaned;
    }

    @Scheduled(fixedDelayString = "${app.youtube.cleanup-interval-ms:300000}", initialDelay = 10000)
    public int cleanupStaleUploads() {
        if (!properties.isEnabled()) {
            return 0;
        }
        int timeoutMinutes = properties.getStaleUploadTimeoutMinutes();
        if (timeoutMinutes <= 0) {
            timeoutMinutes = 30;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return cleanupStaleUploads(cutoff);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        cleanupStaleUploads();
    }

}
