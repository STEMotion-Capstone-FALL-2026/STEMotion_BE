package com.stemotion.core.config;

import com.stemotion.core.security.ResourceAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class ResourceAccessConfig implements WebMvcConfigurer {
    @org.springframework.context.annotation.Bean
    public org.springframework.web.context.request.RequestContextListener authRequestContextListener() {
        return new org.springframework.web.context.request.RequestContextListener();
    }
    private final ResourceAccessInterceptor interceptor;
    private final com.stemotion.core.security.AuthEntryInterceptor authEntry;
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authEntry).addPathPatterns("/api/v1/auth/**", "/api/v1/profile/**", "/api/v1/admin/google-whitelist/**");
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}
