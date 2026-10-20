package com.stemotion.core.support;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.function.Supplier;

/**
 * Memoises lookups for the duration of one HTTP request. The JWT filter, the access interceptor,
 * {@code @PreAuthorize} checks and the services all ask for the same user, memberships and project
 * owner; with open-in-view off each of those asks was its own transaction and round trip to the
 * database. Outside a request (tests, background work) the loader simply runs every time.
 */
public final class RequestCache {

    private static final String PREFIX = "stemotion.cache.";
    private static final Object NONE = new Object();

    private RequestCache() {}

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> loader) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return loader.get();
        String name = PREFIX + key;
        Object cached = attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
        if (cached == null) {
            T loaded = loader.get();
            attributes.setAttribute(name, loaded == null ? NONE : loaded, RequestAttributes.SCOPE_REQUEST);
            return loaded;
        }
        return cached == NONE ? null : (T) cached;
    }

    /** Drops a memoised value, e.g. after the request itself changed it. */
    public static void evict(String key) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) attributes.removeAttribute(PREFIX + key, RequestAttributes.SCOPE_REQUEST);
    }
}
