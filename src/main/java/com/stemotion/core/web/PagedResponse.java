package com.stemotion.core.web;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A paged list keeps the plain JSON array body the front end already reads; the totals travel in
 * headers: {@code X-Total-Count}, {@code X-Total-Pages}, {@code X-Page}, {@code X-Size}.
 */
public final class PagedResponse {

    public static final List<String> HEADERS = List.of("X-Total-Count", "X-Total-Pages", "X-Page", "X-Size");

    private PagedResponse() {}

    public static <T> ResponseEntity<List<T>> of(Page<T> page) {
        return ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(page.getTotalElements()))
            .header("X-Total-Pages", String.valueOf(page.getTotalPages()))
            .header("X-Page", String.valueOf(page.getNumber()))
            .header("X-Size", String.valueOf(page.getSize()))
            .body(page.getContent());
    }

    /** The whole list when no paging was asked for, otherwise the page with its headers. */
    public static <T> ResponseEntity<List<T>> list(Optional<PageParams> paging, Supplier<List<T>> whole,
                                                   Function<PageParams, Page<T>> paged) {
        return paging.map(p -> of(paged.apply(p))).orElseGet(() -> ResponseEntity.ok(whole.get()));
    }
}
