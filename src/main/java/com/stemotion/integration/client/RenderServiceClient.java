package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.time.Duration;

/**
 * Talks to the external Node + Remotion render service.
 *
 * <p>Remotion needs Node and headless Chromium, so rendering cannot run inside
 * this JVM. This API enqueues the job and stores progress; the worker pool owns
 * execution and pushes updates back through the render callback endpoint.
 */
@Slf4j
@Component
public class RenderServiceClient {

    private final RestClient restClient;
    private final IntegrationProperties properties;

    public RenderServiceClient(IntegrationProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getRender().getTimeoutSeconds()));
        this.restClient = RestClient.builder()
            .baseUrl(properties.getRender().getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    public boolean isConfigured() {
        String baseUrl = properties.getRender().getBaseUrl();
        String apiKey = properties.getRender().getApiKey();
        String callbackUrl = properties.getRender().getCallbackUrl();
        return baseUrl != null && !baseUrl.isBlank()
            && apiKey != null && !apiKey.isBlank()
            && callbackUrl != null && !callbackUrl.isBlank();
    }

    /**
     * Enqueues a render and returns the worker's BullMQ job id.
     *
     * @param composition Remotion composition id, e.g. "FullSTEMVideo"
     * @param script      a STEMScript object; FullSTEMVideo takes it as {@code script}
     */
    public EnqueueResult enqueue(
        String jobId,
        String composition,
        Map<String, Object> script,
        int width,
        int height,
        int fps,
        String ttsVoice
    ) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RENDER_NOT_CONFIGURED",
                "Render service URL, shared key and callback URL must be configured");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
            "jobId", jobId,
            "composition", composition,
            "inputProps", Map.of("script", script),
            "width", width,
            "height", height,
            "fps", fps,
            "callbackUrl", properties.getRender().getCallbackUrl()
        ));
        if(ttsVoice!=null && !ttsVoice.isBlank()) body.put("ttsVoice",ttsVoice);

        try {
            JsonNode response = restClient.post()
                .uri("/render")
                .header("X-Api-Key", properties.getRender().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

            if (response == null) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "RENDER_EMPTY_RESPONSE", "Render service returned no body"
                );
            }

            return new EnqueueResult(
                response.path("jobId").asText(jobId),
                response.path("estimatedSeconds").asInt(60)
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Render enqueue failed for job {}", jobId, ex);
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "RENDER_ENQUEUE_FAILED",
                "Could not reach the render service: " + ex.getMessage()
            );
        }
    }

    /** Asks the worker pool to drop a queued job. */
    public void cancel(String externalJobId) {
        try {
            restClient.delete()
                .uri("/render/{jobId}", externalJobId)
                .header("X-Api-Key", properties.getRender().getApiKey())
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Could not cancel render job {}: {}", externalJobId, ex.getMessage());
        }
    }

    public record EnqueueResult(String externalJobId, int estimatedSeconds) {}
}
