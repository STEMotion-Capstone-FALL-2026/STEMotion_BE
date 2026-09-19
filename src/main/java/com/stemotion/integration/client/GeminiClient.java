package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
                "responseSchema", responseSchema
            )
        );

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
        } catch (Exception ex) {
            log.error("Gemini call failed", ex);
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_CALL_FAILED",
                "Gemini request failed: " + ex.getMessage()
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
