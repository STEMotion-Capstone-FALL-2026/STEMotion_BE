package com.stemotion.domain.auth.service;

import com.stemotion.core.security.ResourceAccessInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Metadata-only opt-in: Hibernate validates mappings; no API or DB writes. */
@SpringBootTest(properties = {
    "spring.datasource.hikari.read-only=true",
    "app.cors.allowed-origins=http://localhost:3000",
    "app.integration.render.base-url=",
    "app.integration.render.callback-url=",
    "app.publish.embed-base-url="
})
@ActiveProfiles("cloud")
@EnabledIfEnvironmentVariable(named = "STEMOTION_SCHEMA_READONLY", matches = "true")
class SupabaseSchemaValidationTest {
    @Autowired ResourceAccessInterceptor interceptor;
    @Test void contextAndReadOnlySchemaValidationSucceed() {
        assertThat(interceptor).isNotNull();
    }
}
