package com.stemotion.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final com.stemotion.domain.auth.repository.UserRepository users;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (java.util.Set.of("/api/v1/auth/login", "/api/v1/auth/google", "/api/v1/auth/google/enabled",
                "/api/v1/auth/google/challenge").contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        if (!jwtService.isValid(token)) {
            reject(response, "SESSION_EXPIRED");
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(jwtService.extractUserId(token));
                if (userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    String userId = userDetails.getUsername();
                    com.stemotion.domain.auth.entity.User user = com.stemotion.core.support.RequestCache.get("user:" + userId,
                        () -> users.findForAuthentication(userId)).orElseThrow();
                    AuthSession session = jwtService.session(token, user);
                    var identity = user.getGoogleIdentity();
                    if ("GOOGLE".equals(session.method()) && (session.googleIdentityId() == null
                            || identity == null || !session.googleIdentityId().equals(identity.getId()) || !identity.getWhitelist().isActive()))
                        throw new IllegalArgumentException("Google session revoked");
                    authentication.setDetails(session);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    reject(response, "SESSION_REVOKED");
                    return;
                }
            } catch (org.springframework.dao.DataAccessException ex) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"status\":503,\"code\":\"AUTHENTICATION_UNAVAILABLE\",\"message\":\"Không thể kiểm tra phiên. Vui lòng thử lại.\"}");
                return;
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                reject(response, "SESSION_REVOKED");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, String code) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":401,\"code\":\"" + code
            + "\",\"message\":\"Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.\"}");
    }
}
