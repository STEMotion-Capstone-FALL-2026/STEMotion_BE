package com.stemotion.domain.review.dto;

import java.util.List;

public final class QaDtos {

    private QaDtos() {}

    /** How bad a finding is. FAIL blocks publishing; WARN is advisory. */
    public enum QaSeverity {
        PASS,
        WARN,
        FAIL
    }

    /** The automated checks listed in the project proposal. */
    public enum QaCheck {
        DURATION_BOUNDS,
        NARRATION_PACING,
        ON_SCREEN_TEXT_OVERFLOW,
        CONTRAST_READABILITY,
        SCENE_STRUCTURE,
        RENDER_FRESHNESS,
        CAPTION_SYNC
    }

    public record QaFinding(
        QaCheck check,
        QaSeverity severity,
        String sceneId,
        String message,
        String suggestion
    ) {}

    /**
     * Result of a pre-publish sweep. {@code passed} is false as soon as any
     * check fails, which is what the publish endpoint gates on.
     */
    public record QaReport(
        String projectId,
        boolean passed,
        int failCount,
        int warnCount,
        List<QaFinding> findings
    ) {}
}
