package com.stemotion.domain.workspace.controller;

import com.stemotion.core.config.SecurityConfig;
import com.stemotion.core.security.*;
import com.stemotion.domain.workspace.service.WorkspaceService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Master update plan M15: a workspace leader passes the workspace check (interceptor) but only an
 * administrator adds or removes people; a leader may still change a member's role.
 */
@WebMvcTest(controllers = WorkspaceController.class, properties = "app.cors.allowed-origins=http://localhost:3000")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class WorkspaceMemberAccessTest {
    @Autowired MockMvc mvc;
    @MockBean WorkspaceService workspaces;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService details;
    @MockBean ResourceAccessInterceptor resources;
    @MockBean(name = "workspaceAccess") WorkspaceAccess workspaceAccess;

    static final String ADD = "{\"userId\":\"u\",\"role\":\"WRITER\"}";

    @BeforeEach void leaderPassesTheWorkspaceScope() throws Exception {
        when(resources.preHandle(any(), any(), any())).thenReturn(true);
    }

    @ParameterizedTest @ValueSource(strings = {"WRITER", "REVIEWER", "PRODUCER"})
    void aLeaderCannotAddRemoveOrListCandidates(String role) throws Exception {
        mvc.perform(post("/api/v1/workspaces/ws/members").with(user("leader").roles(role))
            .contentType(MediaType.APPLICATION_JSON).content(ADD)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/workspaces/ws/members/m").with(user("leader").roles(role)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workspaces/ws/candidates").with(user("leader").roles(role)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(workspaces);
    }

    @Test void anAdministratorAddsAndRemoves() throws Exception {
        mvc.perform(post("/api/v1/workspaces/ws/members").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(ADD)).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/workspaces/ws/members/m").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNoContent());
        verify(workspaces).addMember(eq("ws"), any());
        verify(workspaces).removeMember("ws", "m");
    }

    @Test void aLeaderReachesTheRoleChangeWithTheirIdentity() throws Exception {
        mvc.perform(patch("/api/v1/workspaces/ws/members/m").with(user("leader").roles("WRITER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"PRODUCER\"}")).andExpect(status().isOk());
        verify(workspaces).updateMember(eq("ws"), eq("m"), any(), eq("leader"));
    }
}
