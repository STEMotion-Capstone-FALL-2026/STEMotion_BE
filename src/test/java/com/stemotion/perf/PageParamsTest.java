package com.stemotion.perf;

import com.stemotion.core.exception.ApiException;
import com.stemotion.core.web.PageParams;
import com.stemotion.core.web.PagedResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageParamsTest {

    @Test
    void noPagingKeepsTheWholeList() {
        assertThat(PageParams.of(null, null)).isEmpty();
        var response = PagedResponse.list(Optional.empty(), () -> List.of(1, 2, 3), p -> p.slice(List.of(1, 2, 3)));
        assertThat(response.getBody()).containsExactly(1, 2, 3);
        assertThat(response.getHeaders().containsKey("X-Total-Count")).isFalse();
    }

    @Test
    void aPageCarriesItsTotalsInHeaders() {
        List<Integer> all = IntStream.range(0, 45).boxed().toList();
        var response = PagedResponse.list(PageParams.of(2, null), () -> all, p -> p.slice(all));
        assertThat(response.getBody()).containsExactly(40, 41, 42, 43, 44);
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("45");
        assertThat(response.getHeaders().getFirst("X-Total-Pages")).isEqualTo("3");
        assertThat(response.getHeaders().getFirst("X-Page")).isEqualTo("2");
        assertThat(response.getHeaders().getFirst("X-Size")).isEqualTo("20");
        assertThat(PageParams.of(9, 10).orElseThrow().slice(all).getContent()).isEmpty();
    }

    @Test
    void invalidPagingIsRefused() {
        assertThatThrownBy(() -> PageParams.of(-1, 10)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PageParams.of(0, 0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PageParams.of(0, 101)).isInstanceOf(ApiException.class);
        assertThat(PageParams.of(null, 100)).contains(new PageParams(0, 100));
    }
}
