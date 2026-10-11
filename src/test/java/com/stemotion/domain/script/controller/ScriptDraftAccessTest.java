package com.stemotion.domain.script.controller;

import com.stemotion.core.config.SecurityConfig;
import com.stemotion.core.security.*;
import com.stemotion.domain.script.service.ScriptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ScriptController.class, properties = "app.cors.allowed-origins=http://localhost:3000")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ScriptDraftAccessTest {
    @Autowired MockMvc mvc;
    @MockBean ScriptService scripts;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService details;
    @MockBean ResourceAccessInterceptor resources;

    @BeforeEach void allowResourceScopeForRoleChecks() throws Exception {
        when(resources.preHandle(any(), any(), any())).thenReturn(true);
    }
    @Test void guestCannotSaveDraft() throws Exception {
        mvc.perform(put("/api/v1/scripts/project-id").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(scripts);
    }
    @ParameterizedTest @ValueSource(strings = {"PRODUCER", "REVIEWER"})
    void nonWriterCannotSaveDraftEvenWhenResourceScopeAllowsIt(String role) throws Exception {
        mvc.perform(put("/api/v1/scripts/project-id").with(user("owner").roles(role))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(scripts);
    }
    @ParameterizedTest @ValueSource(strings = {"WRITER", "ADMIN"})
    void writerAndAdminReachDraftService(String role) throws Exception {
        mvc.perform(put("/api/v1/scripts/project-id").with(user("owner").roles(role))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());
        verify(scripts).updateDraft(eq("project-id"), any());
    }
}
