package com.stemotion.core.enums;

import com.stemotion.core.enums.StemEnums.RenderResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The render service keeps a 1920x1080 canvas and scales it, so every preset
 * must be 16:9 and resolve from the label the front end sends.
 */
class RenderResolutionTest {

    @Test
    @DisplayName("the 1440p publishing preset resolves from its label and enum name")
    void resolves1440p() {
        assertThat(RenderResolution.fromLabel("1440p")).isEqualTo(RenderResolution.RES_1440P);
        assertThat(RenderResolution.fromLabel("RES_1440P")).isEqualTo(RenderResolution.RES_1440P);
        assertThat(RenderResolution.RES_1440P.getWidth()).isEqualTo(2560);
        assertThat(RenderResolution.RES_1440P.getHeight()).isEqualTo(1440);
    }

    @Test
    @DisplayName("a missing resolution falls back to 1080p")
    void defaultsTo1080p() {
        assertThat(RenderResolution.fromLabel(null)).isEqualTo(RenderResolution.RES_1080P);
    }

    @Test
    @DisplayName("an unknown resolution is rejected")
    void rejectsUnknown() {
        assertThatThrownBy(() -> RenderResolution.fromLabel("8k"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(RenderResolution.class)
    @DisplayName("every preset is 16:9 so it scales the 1920x1080 canvas without distortion")
    void everyPresetIsSixteenByNine(RenderResolution resolution) {
        assertThat(resolution.getWidth() * 9).isEqualTo(resolution.getHeight() * 16);
    }
}
