package com.stemotion.domain.script.mapper;

import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A composed scene may combine blocks freely, but every block must be one the
 * renderer can draw, carrying the content its kind needs.
 */
class ComposedSceneValidatorTest {

    private final ScenePropsValidator validator = new ScenePropsValidator();

    private static Map<String, Object> block(String id, String kind, Object... pairs) {
        Map<String, Object> b = new HashMap<>(Map.of("id", id, "kind", kind));
        for (int i = 0; i < pairs.length; i += 2) b.put((String) pairs[i], pairs[i + 1]);
        return b;
    }

    private static Map<String, Object> scene(List<Map<String, Object>> blocks) {
        Map<String, Object> props = new HashMap<>();
        props.put("frame", "split");
        props.put("ambience", "lab");
        props.put("blocks", new ArrayList<>(blocks));
        return props;
    }

    private static Map<String, Object> gameShow() {
        return scene(List.of(
            block("t", "title", "slot", "top", "text", "Ai nhanh hơn?"),
            block("q", "choice", "slot", "left", "question", "Tăng U gấp đôi thì I…",
                "options", List.of("Giảm", "Không đổi", "Gấp đôi"), "answer", 2, "revealAt", "đáp án"),
            block("f", "formula", "slot", "right", "latex", "I = \\frac{U}{R}", "at", "định luật", "motion", "draw"),
            block("a", "arrow", "from", "q", "to", "f", "text", "vì sao?"),
            block("h", "highlight", "to", "f", "tone", "accent"),
            block("g", "guide", "slot", "bottom", "pose", "point", "text", "Chọn đi nào!")
        ));
    }

    @Test
    @DisplayName("a scene mixing several kinds of blocks passes")
    void acceptsMixedBlocks() {
        assertThatCode(() -> validator.validate(SceneType.COMPOSED, gameShow())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("frame and blocks are required")
    void requiresFrameAndBlocks() {
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, new HashMap<>()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("frame")
            .hasMessageContaining("blocks");
    }

    @Test
    @DisplayName("an unknown frame, slot or kind is rejected")
    void rejectsUnknownEnums() {
        Map<String, Object> badFrame = gameShow();
        badFrame.put("frame", "diagonal");
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, badFrame)).hasMessageContaining("frame must be one of");

        Map<String, Object> badSlot = scene(List.of(block("t", "title", "slot", "ceiling", "text", "x")));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, badSlot)).hasMessageContaining("slot must be one of");

        Map<String, Object> badKind = scene(List.of(block("v", "video")));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, badKind)).hasMessageContaining("kind must be one of");
    }

    @Test
    @DisplayName("each kind must carry its content")
    void rejectsBlocksWithoutContent() {
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, scene(List.of(block("f", "formula")))))
            .hasMessageContaining("needs latex");
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, scene(List.of(block("n", "number", "value", "nhiều")))))
            .hasMessageContaining("value must be a number");
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED,
            scene(List.of(block("c", "chart", "points", List.of(Map.of("label", "A")))))))
            .hasMessageContaining("numeric value");
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, scene(List.of(block("l", "list", "items", List.of())))))
            .hasMessageContaining("items must hold 1 to 6");
    }

    @Test
    @DisplayName("a quiz answer must be one of its options")
    void rejectsAnswerOutsideOptions() {
        Map<String, Object> props = scene(List.of(block("q", "choice", "options", List.of("A", "B"), "answer", 2)));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, props)).hasMessageContaining("answer must point");
    }

    @Test
    @DisplayName("arrows and highlights must point at other blocks of the scene")
    void rejectsDanglingReferences() {
        Map<String, Object> dangling = scene(List.of(
            block("t", "title", "text", "x"), block("a", "arrow", "from", "t", "to", "ghost")));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, dangling)).hasMessageContaining("must name another block");

        Map<String, Object> self = scene(List.of(block("h", "highlight", "to", "h")));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, self)).hasMessageContaining("cannot point at itself");
    }

    @Test
    @DisplayName("block ids must be unique and the block count bounded")
    void rejectsDuplicateIdsAndTooManyBlocks() {
        Map<String, Object> twice = scene(List.of(block("x", "title", "text", "a"), block("x", "text", "text", "b")));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, twice)).hasMessageContaining("used twice");

        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) many.add(block("b" + i, "text", "text", "x"));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, scene(many))).hasMessageContaining("1 to 12 blocks");
    }

    @Test
    @DisplayName("over-long text is rejected so it cannot overflow its slot")
    void rejectsOverlongText() {
        Map<String, Object> props = scene(List.of(block("t", "text", "text", "x".repeat(221))));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, props)).hasMessageContaining("longer than 220");
    }

    @Test
    @DisplayName("an unknown icon in a block is rejected, as in illustrated scenes")
    void rejectsUnknownBlockIcon() {
        Map<String, Object> props = scene(List.of(block("i", "icon", "icon", Map.of("name", "unicorn", "label", "Kỳ lân"))));
        assertThatThrownBy(() -> validator.validate(SceneType.COMPOSED, props)).hasMessageContaining("approved STEM icon set");
    }
}
