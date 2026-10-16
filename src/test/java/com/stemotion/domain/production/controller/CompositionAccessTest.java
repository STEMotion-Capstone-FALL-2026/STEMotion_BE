package com.stemotion.domain.production.controller;

import com.stemotion.core.config.SecurityConfig;
import com.stemotion.core.security.*;
import com.stemotion.domain.production.service.CompositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only the producer of the workspace edits the production draft; members may look at it. */
@WebMvcTest(controllers = CompositionController.class, properties = "app.cors.allowed-origins=http://localhost:3000")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CompositionAccessTest {
    private static final String PATH = "/api/v1/scripts/project-id/composition";
    private static final String BODY = "{\"baseRevision\":0,\"scenes\":[{\"id\":\"s1\",\"type\":\"OUTRO\","
        + "\"title\":\"Kết\",\"durationInFrames\":90}]}";

    @Autowired MockMvc mvc;
    @MockBean CompositionService compositions;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService details;
    @MockBean ResourceAccessInterceptor resources;
    @MockBean(name = "workspaceAccess") WorkspaceAccess workspaceAccess;

    @BeforeEach void allowResourceScopeForRoleChecks() throws Exception {
        when(resources.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test void writerOfTheWorkspaceCannotEditTheProductionDraft() throws Exception {
        // Account role PRODUCER does not count: the workspace role is WRITER.
        when(workspaceAccess.hasProjectRole("project-id", "teacher", "PRODUCER")).thenReturn(false);
        mvc.perform(put(PATH).with(user("teacher").roles("PRODUCER"))
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(compositions);
    }

    @Test void producerOfTheWorkspaceSavesTheDraft() throws Exception {
        when(workspaceAccess.hasProjectRole("project-id", "teacher", "PRODUCER")).thenReturn(true);
        mvc.perform(put(PATH).with(user("teacher").roles("WRITER"))
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());
        verify(compositions).update(eq("project-id"), any(), eq("teacher"));
    }

    @Test void aSaveWithoutBaseRevisionIsRejected() throws Exception {
        when(workspaceAccess.hasProjectRole("project-id", "teacher", "PRODUCER")).thenReturn(true);
        mvc.perform(put(PATH).with(user("teacher").roles("WRITER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"scenes\":[]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(compositions);
    }

    @Test void membersMayOpenTheDraft() throws Exception {
        mvc.perform(get(PATH).with(user("reviewer").roles("REVIEWER"))).andExpect(status().isOk());
        verify(compositions).get("project-id");
    }
}
