package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over the Google AI Studio (Gemini) generateContent endpoint.
 *
 * <p>Calls ask for {@code responseMimeType: application/json} together with a
 * response schema, so the model returns parseable JSON instead of prose that
 * would have to be scraped out of a Markdown fence.
 */
@Slf4j
@Component
public class GeminiClient {

    private final RestClient restClient;
    private final IntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiClient(IntegrationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getGemini().getBaseUrl())
            .build();
    }

    public boolean isConfigured() {
        IntegrationProperties.Gemini gemini = properties.getGemini();
        return gemini.isEnabled() && gemini.getApiKey() != null && !gemini.getApiKey().isBlank();
    }

    /**
     * Sends a prompt and returns the parsed JSON the model produced.
     *
     * @param responseSchema an OpenAPI-subset schema Gemini constrains output to
     */
    public JsonNode generateJson(String systemInstruction, String prompt, Map<String, Object> responseSchema) {
        if (!isConfigured()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_NOT_CONFIGURED",
                "Gemini is not configured; set GEMINI_API_KEY in the environment"
            );
        }

        Map<String, Object> body = Map.of(
            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
            "contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of(
                "temperature", 0.4,
                "responseMimeType", "application/json",
                "responseSchema", responseSchema,
                "maxOutputTokens", 8192
            )
        );

        return callWithRetry(body);
    }

    /**
     * Sends the request, retrying the transient failures Gemini raises when a
     * model is briefly overloaded (503) or the key is rate limited (429).
     *
     * <p>The proposal allows three attempts before a job is escalated. Backoff
     * grows between tries so a busy model is not hammered; anything else, such
     * as a bad key or a malformed request, fails immediately since retrying it
     * would only waste the user's quota.
     */
    private JsonNode callWithRetry(Map<String, Object> body) {
        int maxAttempts = properties.getGemini().getMaxAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                JsonNode response = restClient.post()
                    .uri("/models/{model}:generateContent?key={key}",
                        properties.getGemini().getModel(),
                        properties.getGemini().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

                return parseFirstCandidate(response);
            } catch (ApiException ex) {
                throw ex;
            } catch (HttpServerErrorException.ServiceUnavailable
                     | HttpClientErrorException.TooManyRequests ex) {
                log.warn("Gemini busy on attempt {}/{}: {}", attempt, maxAttempts, ex.getStatusText());
                if (attempt < maxAttempts) {
                    sleepBackoff(attempt);
                }
            } catch (Exception ex) {
                log.error("Gemini call failed", ex);
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_CALL_FAILED",
                    "Gemini request failed: " + ex.getMessage()
                );
            }
        }

        log.error("Gemini still unavailable after {} attempts", maxAttempts);
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AI_BUSY",
            "Mô hình AI đang quá tải. Vui lòng thử lại sau ít phút."
        );
    }

    /** 1s, then 2s, then 4s — enough for a short demand spike to pass. */
    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1000L * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "AI_INTERRUPTED", "AI request was interrupted"
            );
        }
    }

    private JsonNode parseFirstCandidate(JsonNode response) {
        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_EMPTY_RESPONSE", "Gemini returned no body");
        }

        // Thinking models can emit several parts; the answer is the first one
        // carrying text, the rest being thought signatures.
        String text = null;
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            String candidate = part.path("text").asText("");
            if (!candidate.isBlank()) {
                text = candidate;
                break;
            }
        }

        if (text == null) {
            String reason = response.path("candidates").path(0).path("finishReason").asText("UNKNOWN");
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_EMPTY_RESPONSE",
                "Gemini returned no usable content (finishReason=" + reason + ")"
            );
        }

        try {
            return objectMapper.readTree(text);
        } catch (Exception ex) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_INVALID_JSON",
                "Gemini response was not valid JSON: " + ex.getMessage()
            );
        }
    }
}
