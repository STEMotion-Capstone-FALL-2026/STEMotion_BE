package com.stemotion.core.security;
import com.stemotion.core.exception.ApiException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component @RequiredArgsConstructor
public class AuthEntryInterceptor implements HandlerInterceptor {
    private final AuthRateLimiter limiter;
    @Value("${app.auth-v2.enabled:false}") private boolean enabled;
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) return true;
        if (!enabled && !request.getRequestURI().endsWith("/google/enabled"))
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AUTH_V2_DISABLED","Đăng nhập đang được bảo trì.");
        if ("POST".equals(request.getMethod())) {
            try {
                limiter.check("ip:" + request.getRemoteAddr() + ":" + request.getRequestURI());
                if (request.getUserPrincipal() != null)
                    limiter.check("user:" + request.getUserPrincipal().getName() + ":" + request.getRequestURI());
            } catch (ApiException ex) {
                if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) response.setHeader("Retry-After", "300");
                throw ex;
            }
        }
        return true;
    }
}
