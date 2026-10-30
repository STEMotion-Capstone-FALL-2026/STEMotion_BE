package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
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
        return generateJson(type, instruction, prompt, schema, type == com.stemotion.domain.ai.entity.AiRequest.RequestType.SEGMENTATION ? "generate" : "grade");
    }

    public Generation generateJson(com.stemotion.domain.ai.entity.AiRequest.RequestType type,String instruction,String prompt,Map<String,Object> schema,String task) {
        String id=audit.start(type,isRemote() ? "remote:" + task : properties.getGemini().getModel(),new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("prompt",prompt)).toString());
        long started=System.nanoTime();
        Generation generation;
        try { generation=invoke(instruction,prompt,schema,task); }
        catch(RuntimeException ex) {
            audit.complete(id,null,(System.nanoTime()-started)/1_000_000,ex instanceof ApiException api?api.getCode():"AI_CALL_FAILED");
            throw ex;
        }
        audit.complete(id,generation,(System.nanoTime()-started)/1_000_000,null);
        return new Generation(generation.result(),generation.model(),id,generation.inputTokens(),generation.outputTokens(),generation.totalTokens());
    }

    private Generation invoke(String instruction,String prompt,Map<String,Object> schema,String task) {
        if (!isRemote()) {
            var result=embedded.generateWithUsage(instruction,prompt,schema);
            return new Generation(result.result(),properties.getGemini().getModel(),null,result.inputTokens(),result.outputTokens(),result.totalTokens());
        }
        if (!isConfigured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            "AI_NOT_CONFIGURED", "Configure AI_SERVICE_URL and AI_SERVICE_KEY");
        return invokeRemote("/internal/v1/generate", Map.of("systemInstruction", instruction, "prompt", prompt, "responseSchema", schema, "task", task));
    }

    public Generation generateScript(com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest request) {
        return scriptRequest(com.stemotion.domain.ai.entity.AiRequest.RequestType.SEGMENTATION, "/script/generate", request);
    }

    public Generation runAction(com.stemotion.domain.ai.dto.AiDtos.ScriptActionRequest request) {
        var type = request.action() == com.stemotion.domain.ai.dto.AiDtos.AiAction.resegment
            ? com.stemotion.domain.ai.entity.AiRequest.RequestType.SEGMENTATION
            : com.stemotion.domain.ai.entity.AiRequest.RequestType.ANALYSIS;
        return scriptRequest(type, "/script/action", request);
    }

    private Generation scriptRequest(com.stemotion.domain.ai.entity.AiRequest.RequestType type, String path, Object request) {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String id = audit.start(type, "remote", mapper.valueToTree(request).toString());
        long started = System.nanoTime();
        try {
            if (!isRemote()) throw new IllegalStateException("Script endpoints require remote mode");
            Generation result = invokeRemote(path, request);
            audit.complete(id, result, (System.nanoTime() - started) / 1_000_000, null);
            return new Generation(result.result(), result.model(), id, result.inputTokens(), result.outputTokens(), result.totalTokens());
        } catch (RuntimeException ex) {
            audit.complete(id, null, (System.nanoTime() - started) / 1_000_000,
                ex instanceof ApiException api ? api.getCode() : "AI_CALL_FAILED");
            throw ex;
        }
    }

    private Generation invokeRemote(String path, Object payload) {
        if (!isConfigured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            "AI_NOT_CONFIGURED", "Configure AI_SERVICE_URL and AI_SERVICE_KEY");
        try {
            var body = remote.post().uri(path)
                .header("X-AI-Api-Key", properties.getAi().getApiKey())
                .contentType(MediaType.APPLICATION_JSON).body(payload)
                .retrieve().body(JsonNode.class);
            if (body == null || !body.path("result").isObject() || body.path("model").asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "AI service returned an invalid response");
            }
            return new Generation(body.get("result"), body.get("model").asText(), null,
                token(body,"inputTokens"), token(body,"outputTokens"), token(body,"totalTokens"));
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw remoteFailure(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE",
                "Không kết nối được dịch vụ AI (máy chạy AI đang tắt hoặc mất mạng), thử lại sau");
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE", "AI service is unavailable; please try again later");
        }
    }

    /** The AI service answered with an error: say which one instead of "unavailable". */
    static ApiException remoteFailure(int status, String body) {
        String detail = body == null ? "" : body;
        return switch (status) {
            case 401, 403 -> new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_KEY_MISMATCH",
                "Khoá AI_SERVICE_KEY của backend không khớp với dịch vụ AI");
            case 422 -> new ApiException(HttpStatus.BAD_REQUEST, "AI_INVALID_INPUT",
                "Dữ liệu gửi cho AI không hợp lệ, kiểm tra lại nội dung");
            case 429 -> new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_BUSY",
                "Mô hình AI đang quá tải hoặc đã hết hạn mức, thử lại sau ít phút");
            case 504 -> new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT",
                "AI phản hồi quá lâu, thử lại");
            case 502 -> detail.contains("incomplete or invalid")
                ? new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_OUTPUT",
                    "AI trả kết quả chưa đầy đủ, hãy thử tạo lại")
                : new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_FAILED",
                    "Nhà cung cấp AI (OpenAI) báo lỗi, thử lại sau");
            case 503 -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                "Dịch vụ AI chưa được cấu hình đầy đủ");
            default -> new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE",
                "AI service is unavailable; please try again later");
        };
    }

    public boolean isRemote() { return "remote".equals(properties.getAi().getMode()); }
    private static Integer token(JsonNode body,String key) {
        var value=body.path(key);
        return value.isIntegralNumber() && value.canConvertToInt() && value.asInt()>=0?value.asInt():null;
    }
    public record Generation(JsonNode result,String model,String requestId,Integer inputTokens,Integer outputTokens,Integer totalTokens) {
        public Generation(JsonNode result,String model) { this(result,model,null,null,null,null); }
    }
}
