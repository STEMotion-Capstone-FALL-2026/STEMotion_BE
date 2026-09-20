package com.stemotion.domain.script.mapper;

import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The validator is the last gate before a scene reaches the render worker, so
 * it is tested against every scene type the front end can send.
 */
class ScenePropsValidatorTest {

    private final ScenePropsValidator validator = new ScenePropsValidator();

    private static Map<String, Object> quizProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("question", "x1 + x2 = ?");
        props.put("options", List.of("-b/a", "b/a", "c/a", "-c/a"));
        props.put("correctIndex", 0);
        props.put("hint", "Vi-et");
        props.put("explanation", "Tong hai nghiem");
        return props;
    }

    @Test
    @DisplayName("a complete quiz scene passes")
    void acceptsValidQuiz() {
        assertThatCode(() -> validator.validate(SceneType.STEM_QUIZ, quizProps()))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("missing props are all named at once, not one at a time")
    void namesEveryMissingProp() {
        Map<String, Object> props = new HashMap<>();
        props.put("question", "only the question");

        assertThatThrownBy(() -> validator.validate(SceneType.STEM_QUIZ, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("options")
            .hasMessageContaining("correctIndex")
            .hasMessageContaining("hint")
            .hasMessageContaining("explanation");
    }

    @Test
    @DisplayName("a correctIndex outside the options is rejected")
    void rejectsOutOfRangeCorrectIndex() {
        Map<String, Object> props = quizProps();
        props.put("correctIndex", 4);

        assertThatThrownBy(() -> validator.validate(SceneType.STEM_QUIZ, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("outside the options range");
    }

    @Test
    @DisplayName("a quiz with a single answer is rejected")
    void rejectsSingleOptionQuiz() {
        Map<String, Object> props = quizProps();
        props.put("options", List.of("only one"));

        assertThatThrownBy(() -> validator.validate(SceneType.STEM_QUIZ, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("at least two answers");
    }

    @Test
    @DisplayName("an unknown geometry shape is rejected")
    void rejectsUnknownGeometryShape() {
        Map<String, Object> props = new HashMap<>();
        props.put("shapeType", "hypercube");
        props.put("theoremName", "Pythagoras");
        props.put("formulaLatex", "a^2+b^2=c^2");
        props.put("dimensions", Map.of("a", 3, "b", 4, "c", 5));
        props.put("explanation", "…");

        assertThatThrownBy(() -> validator.validate(SceneType.GEOMETRY_SPACE, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("shapeType must be one of");
    }

    @Test
    @DisplayName("geometry dimensions must carry a, b and c")
    void rejectsIncompleteGeometryDimensions() {
        Map<String, Object> props = new HashMap<>();
        props.put("shapeType", "pythagoras_triangle");
        props.put("theoremName", "Pythagoras");
        props.put("formulaLatex", "a^2+b^2=c^2");
        props.put("dimensions", Map.of("a", 3));
        props.put("explanation", "…");

        assertThatThrownBy(() -> validator.validate(SceneType.GEOMETRY_SPACE, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("dimensions must contain a, b and c");
    }

    @Test
    @DisplayName("a comparison side missing its points is rejected")
    void rejectsIncompleteComparisonSide() {
        Map<String, Object> props = new HashMap<>();
        props.put("topicA", Map.of("title", "A", "badge", "1", "points", List.of("p")));
        props.put("topicB", Map.of("title", "B", "badge", "2"));
        props.put("conclusion", "…");

        assertThatThrownBy(() -> validator.validate(SceneType.COMPARISON_SPLIT, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("topicB");
    }

    @Test
    @DisplayName("a null scene type is rejected before anything else")
    void rejectsNullType() {
        assertThatThrownBy(() -> validator.validate(null, Map.of()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Scene type is required");
    }

    /**
     * Guards the contract with stem.ts: every scene type the enum knows about
     * must have a rule here, otherwise a new template would silently accept
     * empty props and fail inside the renderer instead.
     */
    @ParameterizedTest
    @EnumSource(SceneType.class)
    @DisplayName("every scene type declares required props")
    void everySceneTypeHasRules(SceneType type) {
        assertThatThrownBy(() -> validator.validate(type, Map.of()))
            .as("scene type %s has no required props declared", type)
            .isInstanceOf(ApiException.class);

        assertThat(type.name()).isNotBlank();
    }
}
