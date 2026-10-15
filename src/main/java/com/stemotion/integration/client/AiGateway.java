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
    private final com.stemotion.domain.ai.service.AiRequestLogService audit;

    public AiGateway(GeminiClient embedded, IntegrationProperties properties, com.stemotion.domain.ai.service.AiRequestLogService audit) {
        this.embedded = embedded;
        this.audit = audit;
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
        return generateJson(com.stemotion.domain.ai.entity.AiRequest.RequestType.ANALYSIS,instruction,prompt,schema);
    }

    public Generation generateJson(com.stemotion.domain.ai.entity.AiRequest.RequestType type,String instruction,String prompt,Map<String,Object> schema) {
        String id=audit.start(type,properties.getGemini().getModel(),new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("prompt",prompt)).toString());
        long started=System.nanoTime();
        Generation generation;
        try { generation=invoke(instruction,prompt,schema); }
        catch(RuntimeException ex) {
            audit.complete(id,null,(System.nanoTime()-started)/1_000_000,ex instanceof ApiException api?api.getCode():"AI_CALL_FAILED");
            throw ex;
        }
        audit.complete(id,generation,(System.nanoTime()-started)/1_000_000,null);
        return new Generation(generation.result(),generation.model(),id,generation.inputTokens(),generation.outputTokens(),generation.totalTokens());
    }

    private Generation invoke(String instruction,String prompt,Map<String,Object> schema) {
        if (!isRemote()) {
            var result=embedded.generateWithUsage(instruction,prompt,schema);
            return new Generation(result.result(),properties.getGemini().getModel(),null,result.inputTokens(),result.outputTokens(),result.totalTokens());
        }
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
            return new Generation(body.get("result"),body.get("model").asText(),null,token(body,"inputTokens"),token(body,"outputTokens"),token(body,"totalTokens"));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE", "AI service is unavailable; please try again later");
        }
    }

    private boolean isRemote() { return "remote".equals(properties.getAi().getMode()); }
    private static Integer token(JsonNode body,String key) {
        var value=body.path(key);
        return value.isIntegralNumber() && value.canConvertToInt() && value.asInt()>=0?value.asInt():null;
    }
    public record Generation(JsonNode result,String model,String requestId,Integer inputTokens,Integer outputTokens,Integer totalTokens) {
        public Generation(JsonNode result,String model) { this(result,model,null,null,null,null); }
    }
}
