package com.stemotion.core.metrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One log line per API request: method, path (no query string), status, duration and the number of
 * SQL statements it ran. Runs outermost so authentication queries are counted too. Turn off with
 * {@code app.metrics.query-count=false}.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public RequestMetricsFilter(@Value("${app.metrics.query-count:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        QueryCountInspector.reset();
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("api {} {} {} {}ms q={}", request.getMethod(), request.getRequestURI(), response.getStatus(),
                (System.nanoTime() - started) / 1_000_000, QueryCountInspector.count());
        }
    }
}
