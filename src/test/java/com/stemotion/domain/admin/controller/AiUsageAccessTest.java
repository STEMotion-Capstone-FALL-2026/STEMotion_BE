package com.stemotion.domain.admin.controller;

import com.stemotion.core.config.SecurityConfig;
import com.stemotion.core.security.*;
import com.stemotion.domain.admin.service.AiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers=AiUsageController.class,properties="app.cors.allowed-origins=http://localhost:3000")
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class AiUsageAccessTest {
    @Autowired MockMvc mvc;
    @MockBean AiUsageService usage;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService details;
    @MockBean ResourceAccessInterceptor resources;
    @BeforeEach void setup() throws Exception {when(resources.preHandle(any(),any(),any())).thenReturn(true);}
    @Test void onlyAdminReadsAiUsage() throws Exception {
        mvc.perform(get("/api/v1/admin/ai-usage")).andExpect(status().isUnauthorized());
        for(String role:new String[]{"WRITER","PRODUCER","REVIEWER"})
            mvc.perform(get("/api/v1/admin/ai-usage").with(user("user").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(usage);
        mvc.perform(get("/api/v1/admin/ai-usage").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        verify(usage).find(null,null,null,null,0,20);
    }
}
