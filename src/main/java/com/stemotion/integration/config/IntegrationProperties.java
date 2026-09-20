package com.stemotion.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Endpoints of the two services this API orchestrates.
 *
 * <p>Remotion only runs on Node, and the Gemini key must never reach the
 * browser, so both live behind this API rather than being called from the FE.
 */
@Component
@ConfigurationProperties(prefix = "app.integration")
@Getter
@Setter
public class IntegrationProperties {

    private final Render render = new Render();
    private final Gemini gemini = new Gemini();

    @Getter
    @Setter
    public static class Render {
        /** Base URL of the Node + Remotion render service. */
        private String baseUrl = "http://localhost:4000";
        /** Shared secret sent as X-Api-Key on every call. */
        private String apiKey = "";
        /** Where the worker posts progress back to this API. */
        private String callbackUrl = "http://localhost:8080/api/v1/render/callback";
        private int timeoutSeconds = 30;
        /** Matches the retry budget in the project proposal. */
        private int maxAttempts = 3;
    }

    @Getter
    @Setter
    public static class Gemini {
        /** Google AI Studio key. Keep it in .env, never in application.yml. */
        private String apiKey = "";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String model = "gemini-1.5-flash";
        private int timeoutSeconds = 60;
        /** Falls back to a deterministic stub when no key is configured. */
        private boolean enabled = true;
    }
}
