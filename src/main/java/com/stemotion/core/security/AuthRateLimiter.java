package com.stemotion.core.security;
import com.stemotion.core.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.*;

/** Bounded, single-instance sliding windows. Does not trust client-supplied forwarding headers. */
@Component
public class AuthRateLimiter {
    @org.springframework.beans.factory.annotation.Value("${app.auth-v2.rate-limit-attempts:10}")
    private int maxAttempts = 10;
    private final Map<String,ArrayDeque<Long>> windows = new HashMap<>();
    public synchronized void check(String key) {
        long now = System.currentTimeMillis(), before = now - 300_000;
        windows.values().forEach(q -> { while (!q.isEmpty() && q.peekFirst() <= before) q.removeFirst(); });
        windows.entrySet().removeIf(e -> e.getValue().isEmpty());
        if (!windows.containsKey(key) && windows.size() >= 10_000) throw limited();
        var window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (window.size() >= maxAttempts) throw limited();
        window.addLast(now);
    }
    private static ApiException limited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"TOO_MANY_ATTEMPTS","Quá nhiều lần thử. Vui lòng thử lại sau.");
    }
}
