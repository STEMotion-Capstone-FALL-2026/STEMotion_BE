package com.stemotion.domain.script.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scene props travel as a flattened object on the wire and as JSONB in the
 * database, so the round trip through both shapes is what matters here.
 */
class SceneMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SceneMapper mapper = new SceneMapper(objectMapper, new ScenePropsValidator());

    private static SceneRequest mathScene() {
        SceneRequest request = new SceneRequest();
        request.setType(SceneType.MATH_FORMULA);
        request.setTitle("Dinh ly Vi-et");
        request.setNarration("Tong va tich hai nghiem");
        request.setDurationInFrames(90);
        request.putProp("latex", "x_1+x_2=-\\frac{b}{a}");
        request.putProp("steps", List.of(Map.of(
            "label", "B1",
            "latexSnippet", "ax^2+bx+c=0",
            "explanation", "Phuong trinh bac hai"
        )));
        return request;
    }

    @Test
    @DisplayName("base fields map onto the entity, props become JSON")
    void mapsRequestToEntity() {
        Scene scene = mapper.toEntity(mathScene(), 1, new ScriptVersion());

        assertThat(scene.getSceneType()).isEqualTo(SceneType.MATH_FORMULA);
        assertThat(scene.getTitle()).isEqualTo("Dinh ly Vi-et");
        assertThat(scene.getOrderNo()).isEqualTo(1);
        assertThat(scene.getDurationInFrames()).isEqualTo(90);
        assertThat(scene.getProps()).contains("latex").contains("steps");
    }

    @Test
    @DisplayName("an explicit orderNo overrides the position in the list")
    void explicitOrderNoWins() {
        SceneRequest request = mathScene();
        request.setOrderNo(7);

        assertThat(mapper.toEntity(request, 1, new ScriptVersion()).getOrderNo()).isEqualTo(7);
    }

    @Test
    @DisplayName("props survive the trip back out, nested values included")
    void roundTripsProps() {
        Scene scene = mapper.toEntity(mathScene(), 1, new ScriptVersion());
        SceneResponse response = mapper.toResponse(scene);

        assertThat(response.getType()).isEqualTo(SceneType.MATH_FORMULA);
        assertThat(response.getFlattenedProps())
            .containsEntry("latex", "x_1+x_2=-\\frac{b}{a}");

        Object steps = response.getFlattenedProps().get("steps");
        assertThat(steps).isInstanceOf(List.class);
        assertThat((List<?>) steps).hasSize(1);
    }

    @Test
    @DisplayName("a scene with no props reads back as an empty map, not null")
    void emptyPropsAreNeverNull() {
        Scene scene = new Scene();
        scene.setSceneType(SceneType.TITLE_HERO);
        scene.setTitle("Title");
        scene.setDurationInFrames(60);
        scene.setProps(null);

        assertThat(mapper.toResponse(scene).getFlattenedProps()).isEmpty();
    }

    @Test
    @DisplayName("the response serialises with props flattened beside the base fields")
    void serialisesFlattened() throws Exception {
        Scene scene = mapper.toEntity(mathScene(), 1, new ScriptVersion());
        scene.setId("scene-1");

        String json = objectMapper.writeValueAsString(mapper.toResponse(scene));

        // stem.ts expects `latex` at the top level, not nested under "props".
        assertThat(json).contains("\"type\":\"MATH_FORMULA\"");
        assertThat(json).contains("\"latex\"");
        assertThat(json).doesNotContain("\"props\"");
    }
}
