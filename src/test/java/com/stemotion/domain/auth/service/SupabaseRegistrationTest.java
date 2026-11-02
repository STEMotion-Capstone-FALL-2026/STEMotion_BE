package com.stemotion.domain.auth.service;

import com.stemotion.domain.auth.entity.SystemRole;

import com.stemotion.domain.auth.dto.AuthDtos.LoginRequest;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only against an approved isolated auth-v2 schema; fixture writes are rolled back. */
@SpringBootTest(properties = {
    "app.cors.allowed-origins=http://localhost:3000",
    "app.integration.render.base-url=",
    "app.integration.render.callback-url=",
    "app.publish.embed-base-url="
})
@ActiveProfiles("cloud")
@EnabledIfEnvironmentVariable(named = "STEMOTION_AUTH_V2_ISOLATED_SMOKE", matches = "true")
@Transactional
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
class SupabaseRegistrationTest {
    @Autowired AuthService authService;
    @Autowired UserService provisioning;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    private String testUsername;

    @ParameterizedTest
    @EnumSource(value = SystemRole.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void registrationAndLoginPersistNativeEnumsAndUuid(SystemRole role) {
        String username = "deployment-smoke-" + UUID.randomUUID().toString().substring(0,12);
        testUsername = username;
        String password = UUID.randomUUID().toString();
        var registration = provisioning.create(new RegisterRequest(
            "Deployment smoke test", username, password, role));
        entityManager.flush();
        entityManager.clear();

        var stored = users.findById(registration.id()).orElseThrow();
        assertEquals(role, stored.getSystemRole());
        assertEquals(User.UserStatus.ACTIVE, stored.getStatus());
        assertEquals("Deployment smoke test", stored.getFullName());
        assertDoesNotThrow(() -> UUID.fromString(stored.getId()));

        var login = authService.login(new LoginRequest(username, password));
        assertEquals(stored.getId(), login.user().id());
        assertFalse(login.accessToken().isBlank());
        assertEquals(stored.getId(), authService.getCurrentUser(stored.getId()).id());
    }

    @AfterTransaction
    void noTestAccountRemains() {
        assertFalse(users.existsByUsername(testUsername));
    }
}
