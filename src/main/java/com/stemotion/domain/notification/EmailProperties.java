package com.stemotion.domain.notification;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties("app.notification-email") @Getter @Setter
public class EmailProperties {
    private boolean enabled;
    private String provider = "local";
    private String apiKey = "";
    private String fromAddress = "";
    private String fromName = "STEMotion";
    private String publicUrl = "http://localhost:3000";
    private String previewDirectory = "../.audit/mail-preview";
    private int maxAttempts = 5;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int resendCooldownSeconds = 60;
    @PostConstruct
    void validate() {
        if (!enabled) return;
        if (!java.util.Set.of("local", "brevo").contains(provider) || maxAttempts < 1 || maxAttempts > 10
                || connectTimeoutMs < 1 || readTimeoutMs < 1 || resendCooldownSeconds < 1)
            throw new IllegalStateException("Invalid notification email configuration");
        var uri = java.net.URI.create(publicUrl);
        boolean localHttp = "http".equals(uri.getScheme())
            && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost() == null ? "" : uri.getHost());
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || localHttp))
            throw new IllegalStateException("Notification URL requires HTTPS or loopback HTTP for local development");
        if ("brevo".equals(provider) && (apiKey.isBlank() || !fromAddress.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
            throw new IllegalStateException("Real notification email requires an API key and verified sender");
    }
}
