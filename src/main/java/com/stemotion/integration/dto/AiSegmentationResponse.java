package com.stemotion.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSegmentationResponse {

    private List<SceneSegment> scenes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SceneSegment {
        private String narrationText;
        private Integer suggestedDuration;
        private String templateType;
    }
}
