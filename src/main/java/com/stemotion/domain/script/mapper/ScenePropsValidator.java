package com.stemotion.domain.script.mapper;

import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.ai.service.StemIconCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Bean Validation cannot reach inside the free-form props map, so each scene
 * type declares the props its Remotion template needs and they are checked here.
 * This keeps a render job from failing late on a missing prop.
 *
 * <p>Keep this table in sync with the SceneData union in
 * STEMotion_FE/src/types/stem.ts; optional props are deliberately left out.
 */
@Component
public class ScenePropsValidator {

    private static final Map<SceneType, List<String>> REQUIRED_PROPS = Map.ofEntries(
        Map.entry(SceneType.TITLE_HERO, List.of("subtitle", "subject", "gradeLevel", "badgeText")),
        Map.entry(SceneType.MATH_FORMULA, List.of("latex", "steps")),
        Map.entry(SceneType.DIAGRAM_EXPLAINER, List.of("diagramTitle", "labels")),
        Map.entry(SceneType.DATA_CHART, List.of("chartType", "xAxisLabel", "yAxisLabel", "dataPoints")),
        Map.entry(SceneType.ALGORITHM_WALKTHROUGH, List.of("language", "codeSnippet", "steps")),
        Map.entry(SceneType.STEM_QUIZ, List.of("question", "options", "correctIndex", "hint", "explanation")),
        Map.entry(SceneType.OUTRO, List.of("summaryPoints", "nextLessonSuggestion", "instructorName")),
        Map.entry(SceneType.CHEMICAL_REACTION,
            List.of("equation", "reactants", "products", "condition", "observation")),
        Map.entry(SceneType.COMPARISON_SPLIT, List.of("topicA", "topicB", "conclusion")),
        Map.entry(SceneType.PROCESS_TIMELINE, List.of("processTitle", "stages")),
        Map.entry(SceneType.GEOMETRY_SPACE,
            List.of("shapeType", "theoremName", "formulaLatex", "dimensions", "explanation")),
        Map.entry(SceneType.ILLUSTRATED_EXPLAINER, List.of("headline", "icons"))
    );

    private static final List<String> GEOMETRY_SHAPES =
        List.of("pythagoras_triangle", "cone_3d", "circle_trig");

    private static final List<String> FLAT_AMBIENCES = List.of("space", "cell", "lab", "ocean", "lilac", "sky");
    private static final List<String> FLAT_LAYOUTS = List.of("focus", "row", "cluster", "swarm");
    private static final List<String> FLAT_DETAILS = List.of("minimal", "rich");
    private static final int MAX_FLAT_ICONS = 6;

    public void validate(SceneType type, Map<String, Object> props) {
        if (type == null) {
            throw ApiException.badRequest("Scene type is required");
        }

        List<String> missing = REQUIRED_PROPS.getOrDefault(type, List.of()).stream()
            .filter(key -> props == null || props.get(key) == null)
            .toList();

        if (!missing.isEmpty()) {
            throw ApiException.badRequest(
                "Scene of type " + type + " is missing required props: " + String.join(", ", missing)
            );
        }

        switch (type) {
            case STEM_QUIZ -> validateQuiz(props);
            case GEOMETRY_SPACE -> validateGeometry(props);
            case COMPARISON_SPLIT -> validateComparison(props);
            case ILLUSTRATED_EXPLAINER -> validateIllustrated(props);
            default -> { /* the presence check above is enough */ }
        }
    }

    private void validateQuiz(Map<String, Object> props) {
        Object options = props.get("options");
        if (!(options instanceof List<?> optionList) || optionList.size() < 2) {
            throw ApiException.badRequest("STEM_QUIZ options must be a list of at least two answers");
        }
        if (!(props.get("correctIndex") instanceof Number index)) {
            throw ApiException.badRequest("STEM_QUIZ correctIndex must be a number");
        }
        int value = index.intValue();
        if (value < 0 || value >= optionList.size()) {
            throw ApiException.badRequest("STEM_QUIZ correctIndex is outside the options range");
        }
    }

    private void validateGeometry(Map<String, Object> props) {
        Object shapeType = props.get("shapeType");
        if (!(shapeType instanceof String shape) || !GEOMETRY_SHAPES.contains(shape)) {
            throw ApiException.badRequest(
                "GEOMETRY_SPACE shapeType must be one of " + String.join(", ", GEOMETRY_SHAPES)
            );
        }
        if (!(props.get("dimensions") instanceof Map<?, ?> dimensions)
            || !dimensions.keySet().containsAll(List.of("a", "b", "c"))) {
            throw ApiException.badRequest("GEOMETRY_SPACE dimensions must contain a, b and c");
        }
    }

    private void validateComparison(Map<String, Object> props) {
        for (String side : List.of("topicA", "topicB")) {
            if (!(props.get(side) instanceof Map<?, ?> topic)
                || !topic.keySet().containsAll(List.of("title", "badge", "points"))) {
                throw ApiException.badRequest(
                    "COMPARISON_SPLIT " + side + " must contain title, badge and points"
                );
            }
        }
    }

    private void validateIllustrated(Map<String, Object> props) {
        if (!(props.get("icons") instanceof List<?> icons) || icons.isEmpty() || icons.size() > MAX_FLAT_ICONS) {
            throw ApiException.badRequest(
                "ILLUSTRATED_EXPLAINER icons must be a list of 1 to " + MAX_FLAT_ICONS + " icons"
            );
        }
        for (Object icon : icons) {
            if (icon instanceof Map<?, ?> m) {
                Object name = m.get("name");
                if (!(name instanceof String n) || n.isBlank()) {
                    throw ApiException.badRequest("ILLUSTRATED_EXPLAINER icon must have a non-empty name");
                }
                if (!StemIconCatalog.isKnown(n)) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mutableIcon = (Map<String, Object>) m;
                        mutableIcon.put("name", "sparkle");
                    } catch (UnsupportedOperationException ignored) {
                        // Immutable map: Remotion renderer already falls back to a default icon
                    }
                }
            } else {
                throw ApiException.badRequest("ILLUSTRATED_EXPLAINER icon entry must be an object");
            }
        }
        checkOneOf(props, "ambience", FLAT_AMBIENCES);
        checkOneOf(props, "layout", FLAT_LAYOUTS);
        checkOneOf(props, "detail", FLAT_DETAILS);
    }

    /** Optional enum-like prop: absent is fine, an unknown value is not. */
    private void checkOneOf(Map<String, Object> props, String key, List<String> allowed) {
        Object value = props.get(key);
        if (value != null && !allowed.contains(String.valueOf(value))) {
            throw ApiException.badRequest(
                "ILLUSTRATED_EXPLAINER " + key + " must be one of " + String.join(", ", allowed)
            );
        }
    }
}
