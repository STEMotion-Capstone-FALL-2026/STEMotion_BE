package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.ai.entity.AiRequest;
import com.stemotion.domain.ai.service.AiRequestLogService;
import com.stemotion.integration.config.IntegrationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.time.Duration;
import java.util.Map;

@Component
public class AiAlignmentClient {
    private final IntegrationProperties properties;
    private final AiRequestLogService audit;
    private final RestClient remote;
    public AiAlignmentClient(IntegrationProperties properties, AiRequestLogService audit) {
        this.properties = properties; this.audit = audit;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(properties.getAi().getTimeoutSeconds()));
        remote = RestClient.builder().baseUrl(properties.getAi().getBaseUrl()).requestFactory(factory).build();
    }

    public JsonNode align(MultipartFile file, String text, String language) {
        if (file.isEmpty() || file.getSize() > 10L * 1024 * 1024 || text == null || text.isBlank()
                || text.length() > 10000 || language == null || !language.matches("[a-z]{2,3}")) {
            throw ApiException.badRequest("Provide audio up to 10 MiB, known narration and a language code");
        }
        var config = properties.getAi();
        if (config.getBaseUrl().isBlank() || config.getApiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "Alignment service is not configured");
        }
        String id = audit.start(AiRequest.RequestType.CAPTION, "whisper", new com.fasterxml.jackson.databind.ObjectMapper()
            .valueToTree(Map.of("text", text, "language", language)).toString());
        long started = System.nanoTime();
        try {
            var body = new LinkedMultiValueMap<String, Object>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return "scene.audio"; }
            });
            body.add("text", text); body.add("language", language);
            JsonNode response = remote.post().uri("/transcribe/align")
                .header("X-AI-Api-Key", config.getApiKey()).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(JsonNode.class);
            validate(response);
            audit.complete(id, new AiGateway.Generation(response, "whisper:" + response.path("model").asText()),
                (System.nanoTime() - started) / 1_000_000, null);
            return response;
        } catch (Exception ex) {
            String code = ex instanceof ApiException api ? api.getCode() : "AI_ALIGNMENT_FAILED";
            audit.complete(id, null, (System.nanoTime() - started) / 1_000_000, code);
            if (ex instanceof ApiException api) throw api;
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ALIGNMENT_FAILED", "Alignment service failed; review audio and narration");
        }
    }

    private void validate(JsonNode response) {
        if (response == null || !response.path("words").isArray() || response.path("words").isEmpty()
                || !response.path("segments").isArray() || response.path("model").asText().isBlank()) invalid();
        double previous = 0;
        for (JsonNode word : response.path("words")) {
            double start = word.path("start").asDouble(-1), end = word.path("end").asDouble(-1);
            if (word.path("text").asText().isBlank() || !word.path("start").isNumber() || !word.path("end").isNumber()
                    || !Double.isFinite(start) || !Double.isFinite(end) || start < previous || end <= start) invalid();
            previous = end;
        }
    }
    private void invalid() { throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "Invalid word alignment response"); }
}
