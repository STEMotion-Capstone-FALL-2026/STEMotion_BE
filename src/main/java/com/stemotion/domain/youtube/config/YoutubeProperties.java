package com.stemotion.domain.youtube.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.youtube")
@Getter
@Setter
public class YoutubeProperties {
    private boolean enabled = false;
    private String clientId = "";
    private String clientSecret = "";
    private String tokenKey = "";
    private String redirectUri = "http://localhost:8080/api/v1/youtube/oauth/callback";
    private String returnUrl = "http://localhost:3000/workspaces/%s/settings";
    private String authUrl = "https://accounts.google.com/o/oauth2/v2/auth";
    private String tokenUrl = "https://oauth2.googleapis.com/token";
    private String revokeUrl = "https://oauth2.googleapis.com/revoke";
    private String apiBaseUrl = "https://www.googleapis.com";
    private String uploadBaseUrl = "https://www.googleapis.com/upload/youtube/v3/videos";
}
