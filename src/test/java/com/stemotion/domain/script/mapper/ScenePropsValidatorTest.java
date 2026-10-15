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

    private static Map<String, Object> illustratedProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("headline", "Quang hop");
        props.put("ambience", "cell");
        props.put("layout", "focus");
        props.put("icons", List.of(
            Map.of("name", "leaf", "label", "La cay"),
            Map.of("name", "sun", "label", "Anh sang")
        ));
        return props;
    }

    @Test
    @DisplayName("a complete flat-explainer scene passes")
    void acceptsValidIllustrated() {
        assertThatCode(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, illustratedProps()))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ambience and layout may be left out and default in the renderer")
    void acceptsIllustratedWithoutOptionalProps() {
        Map<String, Object> props = illustratedProps();
        props.remove("ambience");
        props.remove("layout");

        assertThatCode(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, props))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an icon outside the approved STEM set is rejected when the scene is saved")
    void rejectsUnknownIcon() {
        // AI drafts have unknown icons replaced before they reach the editor
        // (AiScriptService), so a save carrying one is a client error.
        Map<String, Object> props = illustratedProps();
        props.put("icons", List.of(Map.of("name", "unicorn", "label", "Ky lan")));

        assertThatThrownBy(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("unicorn")
            .hasMessageContaining("approved STEM icon set");
    }

    @Test
    @DisplayName("an icon without a name is rejected")
    void rejectsNamelessIcon() {
        Map<String, Object> props = illustratedProps();
        props.put("icons", List.of(Map.of("label", "?")));

        assertThatThrownBy(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("approved STEM icon set");
    }

    @Test
    @DisplayName("the exchange and zoom layouts are accepted with two or more icons")
    void acceptsStorytellingLayouts() {
        for (String layout : List.of("exchange", "zoom")) {
            Map<String, Object> props = illustratedProps();
            props.put("layout", layout);
            assertThatCode(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, props))
                .doesNotThrowAnyException();

            props.put("icons", List.of(Map.of("name", "leaf")));
            assertThatThrownBy(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, props))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("needs at least 2 icons");
        }
    }

    @Test
    @DisplayName("a flat-explainer scene needs 1 to 6 icons")
    void rejectsEmptyOrOversizedIconList() {
        Map<String, Object> empty = illustratedProps();
        empty.put("icons", List.of());
        assertThatThrownBy(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, empty))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("1 to 6 icons");

        Map<String, Object> tooMany = illustratedProps();
        tooMany.put("icons", java.util.Collections.nCopies(7, Map.of("name", "atom")));
        assertThatThrownBy(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, tooMany))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("1 to 6 icons");
    }

    @Test
    @DisplayName("an unknown ambience or layout is rejected")
    void rejectsUnknownAmbienceOrLayout() {
        Map<String, Object> badAmbience = illustratedProps();
        badAmbience.put("ambience", "jungle");
        assertThatThrownBy(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, badAmbience))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("ambience must be one of");

        Map<String, Object> badLayout = illustratedProps();
        badLayout.put("layout", "spiral");
        assertThatThrownBy(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, badLayout))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("layout must be one of");
    }

    @Test
    @DisplayName("pastel ambiences, the swarm layout and both detail levels are accepted")
    void acceptsNewFlatOptions() {
        Map<String, Object> props = illustratedProps();
        props.put("ambience", "lilac");
        props.put("layout", "swarm");
        props.put("detail", "rich");
        assertThatCode(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, props))
            .doesNotThrowAnyException();

        props.put("detail", "cluttered");
        assertThatThrownBy(() -> validator.validate(SceneType.ILLUSTRATED_EXPLAINER, props))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("detail must be one of");
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
