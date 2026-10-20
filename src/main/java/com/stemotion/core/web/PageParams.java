package com.stemotion.core.web;

import com.stemotion.core.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Optional paging of a list endpoint (DB performance plan, step 4). Without {@code page} and
 * {@code size} the endpoint returns its whole list, as it always has; with either one it returns
 * that page (0-based, default size 20, at most 100) and the totals in {@link PagedResponse} headers.
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static Optional<PageParams> of(Integer page, Integer size) {
        if (page == null && size == null) return Optional.empty();
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) throw ApiException.badRequest("page must be 0 or more");
        if (s < 1 || s > MAX_SIZE) throw ApiException.badRequest("size must be between 1 and " + MAX_SIZE);
        return Optional.of(new PageParams(p, s));
    }

    public Pageable pageable() {
        return PageRequest.of(page, size);
    }

    /** The page of a list already in memory (small lists, or ones filtered after the query). */
    public <T> Page<T> slice(List<T> all) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageImpl<>(all.subList(from, to), pageable(), all.size());
    }
}
