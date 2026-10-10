package com.stemotion.domain.script.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Translates between Scene rows (props held as a JSON string) and the
 * flattened SceneData objects the front end exchanges.
 */
@Component
@RequiredArgsConstructor
public class SceneMapper {

    private static final TypeReference<Map<String, Object>> PROPS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final ScenePropsValidator propsValidator;

    public Scene toEntity(SceneRequest request, int orderNo, ScriptVersion version) {
        propsValidator.validate(request.getType(), request.getProps());
        return Scene.builder()
            .orderNo(request.getOrderNo() == null ? orderNo : request.getOrderNo())
            .sceneType(request.getType())
            .title(request.getTitle())
            .narration(request.getNarration())
            .durationInFrames(request.getDurationInFrames())
            .props(writeProps(request.getProps()))
            .scriptVersion(version)
            .build();
    }

    public void updateEntity(Scene entity, SceneRequest request, int orderNo, ScriptVersion version) {
        Scene values = toEntity(request, orderNo, version);
        entity.setOrderNo(values.getOrderNo());
        entity.setSceneType(values.getSceneType());
        entity.setTitle(values.getTitle());
        entity.setNarration(values.getNarration());
        entity.setDurationInFrames(values.getDurationInFrames());
        entity.setProps(values.getProps());
    }

    /** Old clients stored temporary scene IDs inside props; recover that alias once. */
    public String legacyClientId(Scene scene) {
        try {
            return scene.getProps() == null ? null : objectMapper.readTree(scene.getProps()).path("id").asText(null);
        } catch (Exception ex) { return null; }
    }

    public SceneResponse toResponse(Scene scene) {
        return new SceneResponse(
            scene.getId(),
            scene.getSceneType(),
            scene.getTitle(),
            scene.getNarration(),
            scene.getDurationInFrames(),
            readProps(scene.getProps())
        );
    }

    public List<SceneResponse> toResponses(List<Scene> scenes) {
        return scenes.stream().map(this::toResponse).toList();
    }

    private String writeProps(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(props);
        } catch (Exception ex) {
            throw ApiException.badRequest("Scene props could not be serialised: " + ex.getMessage());
        }
    }

    private Map<String, Object> readProps(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> props = objectMapper.readValue(json, PROPS_TYPE);
            // Legacy props must never shadow authoritative response fields.
            for (String key : List.of("id", "type", "title", "narration", "durationInFrames", "orderNo")) {
                props.remove(key);
            }
            return props;
        } catch (Exception ex) {
            throw ApiException.badRequest("Stored scene props are not valid JSON: " + ex.getMessage());
        }
    }
}
