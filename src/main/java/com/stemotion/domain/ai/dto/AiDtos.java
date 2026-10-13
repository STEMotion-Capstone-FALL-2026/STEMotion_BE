package com.stemotion.domain.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.enums.StemEnums.StemSubject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiDtos {

    private AiDtos() {}

    /** The four on-demand analyses offered in the Writer studio. */
    public enum AiAction {
        /** Re-cut the script into scenes with suggested durations and templates. */
        resegment,
        /** Score readability against the target grade. */
        grade,
        /** Pull out the STEM concepts covered. */
        extract,
        /** Flag inconsistent or wrong scientific terminology. */
        terms
    }

    public record GenerateScriptRequest(
        @NotBlank(message = "topic is required")
        String topic,

        @NotNull(message = "subject is required")
        StemSubject subject,

        @NotBlank(message = "gradeLevel is required")
        String gradeLevel,

        Integer targetDurationSec,

        @Size(max = 500, message = "narrativeBrief must be at most 500 characters")
        String narrativeBrief
    ) {
        public GenerateScriptRequest(String topic, StemSubject subject, String gradeLevel, Integer targetDurationSec) {
            this(topic, subject, gradeLevel, targetDurationSec, null);
        }
    }

    public record ScriptActionRequest(
        @NotNull(message = "action is required")
        AiAction action,

        @NotBlank(message = "scriptText is required")
        String scriptText,

        StemSubject subject,

        String gradeLevel
    ) {}

    /**
     * Raw model output plus the metadata the UI shows next to each suggestion.
     * The writer accepts or ignores it, so nothing is applied automatically.
     */
    public record AiActionResponse(
        AiAction action,
        JsonNode result,
        String model,
        long elapsedMs,
        String requestId
    ) {
        public AiActionResponse(AiAction action,JsonNode result,String model,long elapsedMs) {
            this(action,result,model,elapsedMs,null);
        }
    }

    public record AiDecisionRequest(@NotNull com.stemotion.domain.ai.entity.AiRequest.Decision decision) {}

    /** Suggestions returned by {@code resegment}, ready to become scenes. */
    public record SuggestedScene(
        String type,
        String title,
        String narration,
        Integer suggestedDurationSec,
        List<String> concepts,
        List<String> suggestedIcons
    ) {}
}
