package com.stemotion.core.enums;

/**
 * Shared vocabulary of the STEMotion pipeline.
 *
 * <p>These names are part of the contract with the front end
 * (see STEMotion_FE/src/types/stem.ts) and are serialised as-is.
 */
public final class StemEnums {

    private StemEnums() {}

    /** Subjects a clip can belong to. */
    public enum StemSubject {
        Math,
        Physics,
        Chemistry,
        Biology,
        ComputerScience
    }

    /** The Remotion scene templates, mirroring SceneType in stem.ts. */
    public enum SceneType {
        TITLE_HERO,
        MATH_FORMULA,
        DIAGRAM_EXPLAINER,
        DATA_CHART,
        ALGORITHM_WALKTHROUGH,
        STEM_QUIZ,
        OUTRO,
        CHEMICAL_REACTION,
        COMPARISON_SPLIT,
        PROCESS_TIMELINE,
        GEOMETRY_SPACE
    }

    /** Editorial state of the written script. */
    public enum ScriptStatus {
        DRAFT,
        IN_REVIEW,
        APPROVED,
        CHANGE_REQUESTED
    }

    /** State of the rendered clip that follows an approved script. */
    public enum VideoStatus {
        NOT_RENDERED,
        RENDERING,
        IN_QA,
        APPROVED,
        PUBLISHED
    }

    /** Lifecycle of a Remotion render job handled by the worker pool. */
    public enum RenderStatus {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    /** Resolution presets offered to the producer. */
    public enum RenderResolution {
        RES_720P("720p", 1280, 720),
        RES_1080P("1080p", 1920, 1080),
        RES_4K("4k", 3840, 2160);

        private final String label;
        private final int width;
        private final int height;

        RenderResolution(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        public String getLabel() {
            return label;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        /** Accepts the front-end spelling ("1080p") as well as the enum name. */
        public static RenderResolution fromLabel(String value) {
            if (value == null) {
                return RES_1080P;
            }
            for (RenderResolution resolution : values()) {
                if (resolution.label.equalsIgnoreCase(value) || resolution.name().equalsIgnoreCase(value)) {
                    return resolution;
                }
            }
            throw new IllegalArgumentException("Unsupported resolution: " + value);
        }
    }

    /** Whether a review note still needs work. */
    public enum FeedbackStatus {
        OPEN,
        RESOLVED
    }

    /** Where an approved clip gets published. */
    public enum PublishTarget {
        LMS,
        YOUTUBE
    }

    /** Membership state inside a workspace. */
    public enum MemberStatus {
        ACTIVE,
        INVITED
    }
}
