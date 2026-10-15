package com.stemotion.domain.admin.controller;

import com.stemotion.core.config.SecurityConfig;
import com.stemotion.core.security.*;
import com.stemotion.domain.admin.service.AdminMetricsService;
import com.stemotion.domain.auth.controller.UserController;
import com.stemotion.domain.auth.service.UserService;
import com.stemotion.domain.production.controller.TemplateController;
import com.stemotion.domain.production.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AdminController.class, UserController.class, TemplateController.class},
    properties = "app.cors.allowed-origins=http://localhost:3000")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminPortalAccessTest {
    @Autowired MockMvc mvc;
    @MockBean AdminMetricsService metrics;
    @MockBean UserService users;
    @MockBean TemplateService templates;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService details;
    @MockBean ResourceAccessInterceptor resources;

    @BeforeEach void allowUnrelatedResourceInterceptor() throws Exception {
        when(resources.preHandle(any(), any(), any())).thenReturn(true);
    }

    @ParameterizedTest @ValueSource(strings = {"/metrics", "/overview", "/render-jobs"})
    void guestCannotReadAdminMetrics(String path) throws Exception {
        mvc.perform(get("/api/v1/admin" + path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(metrics);
    }

    @ParameterizedTest @ValueSource(strings = {"WRITER", "REVIEWER", "PRODUCER"})
    void ordinaryRolesCannotReadAdminMetricsOrManageUsers(String role) throws Exception {
        for (String path : new String[]{"/api/v1/admin/metrics", "/api/v1/admin/overview", "/api/v1/admin/render-jobs", "/api/v1/users"}) {
            mvc.perform(get(path).with(user("ordinary").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(delete("/api/v1/users/another-user").with(user("ordinary").roles(role)))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/templates/template-id").with(user("ordinary").roles(role)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(metrics, users, templates);
    }

    @Test void adminCanReadAllOverviewEndpoints() throws Exception {
        for (String path : new String[]{"/metrics", "/overview", "/render-jobs"}) {
            mvc.perform(get("/api/v1/admin" + path).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        }
        verify(metrics).collect();
        verify(metrics).collectOverview();
        verify(metrics).listRecentRenderJobs();
    }
}
