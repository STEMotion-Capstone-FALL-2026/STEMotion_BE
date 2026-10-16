package com.stemotion.integration.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import com.stemotion.integration.config.IntegrationProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiGatewayTest {
    @Test void embeddedUsageAndFailureBothReachPersistentAuditBoundary() throws Exception {
        var embedded=mock(GeminiClient.class);
        var audit=mock(com.stemotion.domain.ai.service.AiRequestLogService.class);
        var properties=new IntegrationProperties();
        when(audit.start(any(),anyString(),anyString())).thenReturn("request-id");
        var payload=new ObjectMapper().readTree("{\"score\":80}");
        when(embedded.generateWithUsage(anyString(),anyString(),anyMap()))
            .thenReturn(new GeminiClient.UsageResult(payload,12,8,20));
        var gateway=new AiGateway(embedded,properties,audit);
        var result=gateway.generateJson("STEM","Force",Map.of());
        assertThat(result.requestId()).isEqualTo("request-id");
        assertThat(result.totalTokens()).isEqualTo(20);
        verify(audit).complete(eq("request-id"),argThat(g->g!=null && g.inputTokens()==12 && g.outputTokens()==8),anyLong(),isNull());
        when(embedded.generateWithUsage(anyString(),anyString(),anyMap())).thenThrow(new RuntimeException("provider-secret"));
        assertThatThrownBy(()->gateway.generateJson("STEM","Force",Map.of())).isInstanceOf(RuntimeException.class);
        verify(audit).complete(eq("request-id"),isNull(),anyLong(),eq("AI_CALL_FAILED"));
    }
    @Test
    void remoteBoundaryUsesServiceKeyAndReturnsActualProviderModel() throws Exception {
        var embedded = mock(GeminiClient.class);
        var properties = new IntegrationProperties();
        properties.getAi().setMode("remote");
        properties.getAi().setApiKey("internal-test-key");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new AtomicReference<String>();
        var key = new AtomicReference<String>();
        server.createContext("/internal/v1/generate", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("X-AI-Api-Key"));
            byte[] response = "{\"result\":{\"concepts\":[]},\"model\":\"remote-gemini\",\"elapsedMs\":12}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            properties.getAi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var result = new AiGateway(embedded, properties, mock(com.stemotion.domain.ai.service.AiRequestLogService.class)).generateJson("STEM", "Atoms", Map.of("type", "OBJECT"));
            assertThat(result.model()).isEqualTo("remote-gemini");
            assertThat(result.result().path("concepts").isArray()).isTrue();
            assertThat(key.get()).isEqualTo("internal-test-key");
            var submitted = new ObjectMapper().readTree(body.get());
            assertThat(submitted.path("prompt").asText()).isEqualTo("Atoms");
            assertThat(submitted.path("responseSchema").path("type").asText()).isEqualTo("OBJECT");
            verifyNoInteractions(embedded);
        } finally { server.stop(0); }
    }

    @Test
    void missingRemoteConfigurationNeverFallsBackToEmbeddedGemini() {
        var properties = new IntegrationProperties();
        properties.getAi().setMode("remote");
        var embedded = mock(GeminiClient.class);
        var gateway = new AiGateway(embedded, properties, mock(com.stemotion.domain.ai.service.AiRequestLogService.class));
        assertThat(gateway.isConfigured()).isFalse();
        assertThatThrownBy(() -> gateway.generateJson("STEM", "Atoms", Map.of()))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("AI_NOT_CONFIGURED"));
        verifyNoInteractions(embedded);
    }

    @Test void scriptBoundarySendsStructuredInputRatherThanJavaPrompt() throws Exception {
        var properties = new IntegrationProperties();
        properties.getAi().setMode("remote"); properties.getAi().setApiKey("test-key");
        var embedded = mock(GeminiClient.class);
        var audit = mock(com.stemotion.domain.ai.service.AiRequestLogService.class);
        when(audit.start(any(), anyString(), anyString())).thenReturn("audit-id");
        var submitted = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/script/generate", exchange -> {
            submitted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestHeaders().getFirst("X-AI-Api-Key")).isEqualTo("test-key");
            var response = "{\"result\":{\"isValidStemTopic\":false,\"scenes\":[]},\"model\":\"actual-model\",\"inputTokens\":12,\"outputTokens\":8}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        }); server.start();
        try {
            properties.getAi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var result = new AiGateway(embedded, properties, audit).generateScript(
                new com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest("Ohm",
                    com.stemotion.core.enums.StemEnums.StemSubject.Physics, "10", 0, "gameshow", "interview"));
            var body = new ObjectMapper().readTree(submitted.get());
            assertThat(body.path("narrativeBrief").asText()).isEqualTo("gameshow");
            assertThat(body.path("approach").asText()).isEqualTo("interview");
            assertThat(body.has("prompt")).isFalse();
            assertThat(result.requestId()).isEqualTo("audit-id");
            assertThat(result.model()).isEqualTo("actual-model");
            verify(audit).complete(eq("audit-id"), any(), anyLong(), isNull());
            verifyNoInteractions(embedded);
        } finally { server.stop(0); }
    }
}
