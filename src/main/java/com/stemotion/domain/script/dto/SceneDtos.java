package com.stemotion.domain.script.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.stemotion.core.enums.StemEnums.SceneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scene payloads in the shape the front end's SceneData union expects:
 * the common SceneBase fields plus the template-specific props flattened
 * onto the same object rather than nested under a "props" key.
 */
public final class SceneDtos {

    private SceneDtos() {}

    /**
     * Incoming scene. Any field that is not part of SceneBase is collected
     * into {@link #getProps()} and persisted as JSONB.
     */
    @Getter
    @Setter
    public static class SceneRequest {

        /** Existing server ID; local non-UUID IDs are temporary identities for new scenes. */
        private String id;

        @NotNull(message = "type is required")
        private SceneType type;

        @NotBlank(message = "title is required")
        private String title;

        private String narration;

        @NotNull(message = "durationInFrames is required")
        private Integer durationInFrames;

        /** Explicit order; when null the position in the submitted array wins. */
        private Integer orderNo;

        @JsonIgnore
        private final Map<String, Object> props = new LinkedHashMap<>();

        @JsonAnySetter
        public void putProp(String key, Object value) {
            props.put(key, value);
        }
    }

    /** Outgoing scene, flattened back into the SceneData shape. */
    @Getter
    public static class SceneResponse {

        private final String id;
        private final SceneType type;
        private final String title;
        private final String narration;
        private final Integer durationInFrames;

        @JsonIgnore
        private final Map<String, Object> props;

        public SceneResponse(
            String id,
            SceneType type,
            String title,
            String narration,
            Integer durationInFrames,
            Map<String, Object> props
        ) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.narration = narration;
            this.durationInFrames = durationInFrames;
            this.props = props == null ? Map.of() : props;
        }

        @JsonAnyGetter
        public Map<String, Object> getFlattenedProps() {
            return props;
        }
    }
}
