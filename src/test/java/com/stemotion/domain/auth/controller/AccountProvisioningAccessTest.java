package com.stemotion.domain.auth.controller;

import com.stemotion.core.config.SecurityConfig;
import com.stemotion.core.security.*;
import com.stemotion.domain.auth.service.*;
import com.stemotion.domain.auth.dto.AuthDtos.UserResponse;
import com.stemotion.domain.auth.entity.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UserController.class}, properties = "app.cors.allowed-origins=http://localhost:3000")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AccountProvisioningAccessTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService auth;
    @MockBean UserService users;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService details;
    @MockBean ResourceAccessInterceptor resources;
    private static final String BODY = """
        {"fullName":"Provisioned teacher","email":"teacher@example.invalid","password":"test-password-only","role":"WRITER"}
        """;

    @BeforeEach void allowUnrelatedResourceInterceptor() throws Exception {
        when(resources.preHandle(any(), any(), any())).thenReturn(true);
    }
    @Test void guestCannotProvisionAnAccount() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }
    @ParameterizedTest @ValueSource(strings = {"WRITER", "REVIEWER", "PRODUCER"})
    void ordinaryRoleCannotProvision(String role) throws Exception {
        mvc.perform(post("/api/v1/users").with(user("existing").roles(role)).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }
    @Test void adminCreatesAccountWithoutReceivingItsSession() throws Exception {
        when(users.create(any())).thenReturn(new UserResponse("new-id", "Teacher", "teacher@example.invalid", User.UserRole.WRITER, User.UserStatus.ACTIVE, null));
        mvc.perform(post("/api/v1/users").with(user("admin-id").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("new-id"))
            .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(users).create(any());
        verifyNoInteractions(auth);
    }
    @Test void legacyRegisterIsDisabledForGuestAndAdmin() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(auth);
    }
    @Test void invalidProvisioningRequestIsRejectedBeforePersistence() throws Exception {
        mvc.perform(post("/api/v1/users").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }
}
