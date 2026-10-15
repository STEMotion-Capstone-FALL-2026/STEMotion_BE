package com.stemotion.integration.client;

import com.stemotion.domain.ai.service.AiRequestLogService;
import com.stemotion.integration.config.IntegrationProperties;
import com.stemotion.core.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiAlignmentClientTest {
    @Test void forwardsAudioKnownTextAndAuditsWordTiming() throws Exception {
        var properties=new IntegrationProperties(); properties.getAi().setApiKey("internal-key");
        var audit=mock(AiRequestLogService.class);
        when(audit.start(any(),anyString(),anyString())).thenReturn("audit-id");
        var sent=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/transcribe/align",exchange->{
            assertThat(exchange.getRequestHeaders().getFirst("X-AI-Api-Key")).isEqualTo("internal-key");
            sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response="{\"model\":\"small\",\"words\":[{\"text\":\"Ohm\",\"start\":0.1,\"end\":0.5}],\"segments\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,response.length);
            try(var out=exchange.getResponseBody()){out.write(response);}
        });server.start();
        try{
            properties.getAi().setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort());
            var result=new AiAlignmentClient(properties,audit).align(new MockMultipartFile("file","x.wav","audio/wav","audio-bytes".getBytes()),"Ohm","vi");
            assertThat(result.path("words").get(0).path("end").asDouble()).isEqualTo(0.5);
            assertThat(sent.get()).contains("audio-bytes","Ohm","vi");
            verify(audit).complete(eq("audit-id"),argThat(g->g.model().equals("whisper:small") && g.inputTokens()==null),anyLong(),isNull());
        }finally{server.stop(0);}
    }
    @Test void emptyAudioFailsBeforeNetworkAndAudit(){
        var audit=mock(AiRequestLogService.class);
        assertThatThrownBy(()->new AiAlignmentClient(new IntegrationProperties(),audit).align(
            new MockMultipartFile("file",new byte[0]),"Ohm","vi")).isInstanceOf(ApiException.class);
        verifyNoInteractions(audit);
    }
}
