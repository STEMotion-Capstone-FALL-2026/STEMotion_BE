package com.stemotion.domain.script.service;

import com.stemotion.core.exception.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewReasonTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void returningAScriptOrClipWithoutAReasonIsRefused(String note) {
        assertThatThrownBy(() -> ScriptService.requireReason(note))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo("REVIEW_REASON_REQUIRED");
                assertThat(ex.getStatus().value()).isEqualTo(400);
            });
    }

    @Test
    void theReasonIsKeptTrimmed() {
        assertThat(ScriptService.requireReason("  Sai công thức ở cảnh 2 ")).isEqualTo("Sai công thức ở cảnh 2");
    }
}
