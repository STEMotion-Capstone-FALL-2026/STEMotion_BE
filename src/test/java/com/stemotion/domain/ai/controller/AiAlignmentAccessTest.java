package com.stemotion.domain.ai.controller;

import com.stemotion.core.config.SecurityConfig;
import com.stemotion.core.security.*;
import com.stemotion.integration.client.AiAlignmentClient;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers=AiAlignmentController.class,properties="app.cors.allowed-origins=http://localhost:3000")
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class AiAlignmentAccessTest {
    @Autowired MockMvc mvc;
    @MockBean AiAlignmentClient client;
    @MockBean IntegrationProperties properties;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService details;
    @MockBean ResourceAccessInterceptor resources;
    final IntegrationProperties config=new IntegrationProperties();
    @BeforeEach void setup() throws Exception {
        when(properties.getAi()).thenReturn(config.getAi());
        when(resources.preHandle(any(),any(),any())).thenReturn(true);
    }
    MockMultipartFile file(){return new MockMultipartFile("file","x.wav","audio/wav",new byte[]{1});}
    @Test void onlyAuthorizedRolesUsePublicAlignment() throws Exception {
        mvc.perform(multipart("/api/v1/ai/transcribe/align").file(file()).param("text","Ohm")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/ai/transcribe/align").file(file()).param("text","Ohm").with(user("review").roles("REVIEWER"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/ai/transcribe/align").file(file()).param("text","Ohm").with(user("producer").roles("PRODUCER"))).andExpect(status().isOk());
        verify(client).align(any(),eq("Ohm"),eq("vi"));
    }
    @Test void workerMustHaveSeparateConfiguredKey() throws Exception {
        var path="/api/v1/ai/internal/transcribe/align";
        mvc.perform(multipart(path).file(file()).param("text","Ohm")).andExpect(status().isServiceUnavailable());
        config.getAi().setAlignmentWorkerKey("worker-key");
        mvc.perform(multipart(path).file(file()).param("text","Ohm")).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path).file(file()).param("text","Ohm").header("X-AI-Alignment-Key","wrong")).andExpect(status().isUnauthorized());
        verifyNoInteractions(client);
        mvc.perform(multipart(path).file(file()).param("text","Ohm").header("X-AI-Alignment-Key","worker-key")).andExpect(status().isOk());
    }
}
