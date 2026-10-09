package com.stemotion.domain.auth.service;

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

/** Opt-in against the existing schema; each registration is rolled back. */
@SpringBootTest(properties = {
    "app.cors.allowed-origins=http://localhost:3000",
    "app.integration.render.base-url=",
    "app.integration.render.callback-url=",
    "app.publish.embed-base-url="
})
@ActiveProfiles("cloud")
@EnabledIfEnvironmentVariable(named = "STEMOTION_DB_SMOKE", matches = "true")
@Transactional
class SupabaseRegistrationTest {
    @Autowired AuthService authService;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    private String testEmail;

    @ParameterizedTest
    @EnumSource(User.UserRole.class)
    void registrationAndLoginPersistNativeEnumsAndUuid(User.UserRole role) {
        String email = "deployment-smoke-" + UUID.randomUUID() + "@example.invalid";
        testEmail = email;
        String password = UUID.randomUUID().toString();
        var registration = authService.register(new RegisterRequest(
            "Deployment smoke test", email, password, role));
        entityManager.flush();
        entityManager.clear();

        var stored = users.findById(registration.user().id()).orElseThrow();
        assertEquals(role, stored.getRole());
        assertEquals(User.UserStatus.ACTIVE, stored.getStatus());
        assertEquals("Deployment smoke test", stored.getFullName());
        assertDoesNotThrow(() -> UUID.fromString(stored.getId()));

        var login = authService.login(new LoginRequest(email, password));
        assertEquals(stored.getId(), login.user().id());
        assertFalse(login.accessToken().isBlank());
        assertEquals(stored.getId(), authService.getCurrentUser(stored.getId()).id());
    }

    @AfterTransaction
    void noTestAccountRemains() {
        assertFalse(users.existsByEmail(testEmail));
    }
}
