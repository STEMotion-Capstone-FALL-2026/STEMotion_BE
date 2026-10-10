package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.Map;

/** User authorization stays in BE; remote mode executes the model in FastAPI. */
@Component
public class AiGateway {
    private final GeminiClient embedded;
    private final IntegrationProperties properties;
    private final RestClient remote;

    public AiGateway(GeminiClient embedded, IntegrationProperties properties) {
        this.embedded = embedded;
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(properties.getAi().getTimeoutSeconds()));
        remote = RestClient.builder().baseUrl(properties.getAi().getBaseUrl()).requestFactory(factory).build();
        if (!java.util.Set.of("embedded", "remote").contains(properties.getAi().getMode())) {
            throw new IllegalArgumentException("AI_SERVICE_MODE must be embedded or remote");
        }
    }

    public boolean isConfigured() {
        if (!isRemote()) return embedded.isConfigured();
        return !properties.getAi().getApiKey().isBlank() && !properties.getAi().getBaseUrl().isBlank();
    }

    public Generation generateJson(String instruction, String prompt, Map<String, Object> schema) {
        if (!isRemote()) return new Generation(embedded.generateJson(instruction, prompt, schema), properties.getGemini().getModel());
        if (!isConfigured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            "AI_NOT_CONFIGURED", "Configure AI_SERVICE_URL and AI_SERVICE_KEY");
        try {
            var body = remote.post().uri("/internal/v1/generate")
                .header("X-AI-Api-Key", properties.getAi().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("systemInstruction", instruction, "prompt", prompt, "responseSchema", schema))
                .retrieve().body(JsonNode.class);
            if (body == null || !body.path("result").isObject() || body.path("model").asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "AI service returned an invalid response");
            }
            return new Generation(body.get("result"), body.get("model").asText());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE", "AI service is unavailable; please try again later");
        }
    }

    private boolean isRemote() { return "remote".equals(properties.getAi().getMode()); }
    public record Generation(JsonNode result, String model) {}
}
