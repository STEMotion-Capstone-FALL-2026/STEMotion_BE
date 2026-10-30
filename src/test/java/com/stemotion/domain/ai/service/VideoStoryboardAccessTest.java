package com.stemotion.domain.ai.service;

import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.ai.controller.VideoStoryboardController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(VideoStoryboardAccessTest.Config.class)
class VideoStoryboardAccessTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean WorkspaceAccess workspaceAccess() {return mock(WorkspaceAccess.class);}
        @Bean VideoStoryboardService service() {return mock(VideoStoryboardService.class);}
        @Bean VideoStoryboardController controller(VideoStoryboardService service) {return new VideoStoryboardController(service);}
    }
    @Autowired VideoStoryboardController controller;
    @Autowired WorkspaceAccess access;
    @Autowired VideoStoryboardService service;
    @BeforeEach void resetMocks() { reset(access, service); }
    @Test @WithMockUser(username="member") void wrongProjectRoleDoesNotConsumeAi() {
        assertThatThrownBy(() -> controller.suggest("p", new VideoStoryboardController.StoryboardRequest(1,0)))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(username="producer") void scopedProducerMayRequestSuggestion() {
        when(access.hasProjectRole("p", "producer", "WRITER", "PRODUCER")).thenReturn(true);
        controller.suggest("p", new VideoStoryboardController.StoryboardRequest(1,0));
        verify(service).suggest("p",1,0);
    }
}
