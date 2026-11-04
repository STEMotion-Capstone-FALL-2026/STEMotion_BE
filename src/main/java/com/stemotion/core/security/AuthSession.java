package com.stemotion.core.security;
import org.springframework.security.core.context.SecurityContextHolder;
/** Method is read exclusively from the verified application JWT. */
public record AuthSession(String method, String googleIdentityId) {
    public static AuthSession current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof AuthSession s ? s : new AuthSession("PASSWORD", null);
    }
    public boolean isPassword() { return "PASSWORD".equals(method); }
}
