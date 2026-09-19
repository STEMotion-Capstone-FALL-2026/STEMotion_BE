package com.stemotion.domain.script.mapper;

import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Bean Validation cannot reach inside the free-form props map, so each scene
 * type declares the props its Remotion template needs and they are checked here.
 * This keeps a render job from failing late on a missing prop.
 */
@Component
public class ScenePropsValidator {

    private static final Map<SceneType, List<String>> REQUIRED_PROPS = Map.of(
        SceneType.TITLE_HERO, List.of("subtitle", "subject", "gradeLevel", "badgeText"),
        SceneType.MATH_FORMULA, List.of("latex", "steps"),
        SceneType.DIAGRAM_EXPLAINER, List.of("diagramTitle", "labels"),
        SceneType.DATA_CHART, List.of("chartType", "xAxisLabel", "yAxisLabel", "dataPoints"),
        SceneType.ALGORITHM_WALKTHROUGH, List.of("language", "codeSnippet", "steps"),
        SceneType.STEM_QUIZ, List.of("question", "options", "correctIndex", "hint", "explanation"),
        SceneType.OUTRO, List.of("summaryPoints", "nextLessonSuggestion", "instructorName")
    );

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

        if (type == SceneType.STEM_QUIZ) {
            validateQuiz(props);
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
}
