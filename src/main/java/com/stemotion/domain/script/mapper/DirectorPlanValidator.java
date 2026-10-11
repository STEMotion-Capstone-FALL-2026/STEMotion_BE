package com.stemotion.domain.script.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Build-time mirror of the FE registry. No component names from user input are executed. */
public final class DirectorPlanValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNode REGISTRY;
    static {
        try (var in = DirectorPlanValidator.class.getResourceAsStream("/director-registry.json")) {
            if (in == null) throw new IllegalStateException("Director registry missing");
            REGISTRY = JSON.readTree(in);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private DirectorPlanValidator() {}
    private static void require(boolean valid, String message) {
        if (!valid) throw ApiException.badRequest("Scene Plan: " + message);
    }
    private static void keys(JsonNode node, List<String> allowed) {
        require(node.isObject(), "expected object");
        node.fieldNames().forEachRemaining(key -> require(allowed.contains(key), "unexpected property " + key));
    }
    public static void validate(Object value) {
        JsonNode plan = JSON.valueToTree(value);
        keys(plan, List.of("template_id", "assets", "narration", "duration_sec"));
        String template = plan.path("template_id").asText();
        require(REGISTRY.path("templates").has(template), "unknown template_id");
        require(plan.path("narration").isTextual() && plan.path("narration").asText().length() <= 6000, "invalid narration");
        double seconds = plan.path("duration_sec").asDouble(Double.NaN);
        require(plan.path("duration_sec").isNumber() && Double.isFinite(seconds) && seconds >= 1 && seconds <= 120, "invalid duration_sec");
        JsonNode assets = plan.path("assets"), slots = REGISTRY.path("templates").path(template).path("slots");
        require(assets.isArray() && assets.size() >= 1 && assets.size() <= 8, "expected 1–8 assets");
        Set<String> used = new HashSet<>();
        for (JsonNode asset : assets) {
            keys(asset, List.of("asset_id", "slot", "props"));
            String id = asset.path("asset_id").asText(), slot = asset.path("slot").asText();
            require(REGISTRY.path("assets").has(id), "unknown asset_id");
            boolean allowed = false;
            for (JsonNode candidate : slots.path(slot)) if (candidate.asText().equals(id)) allowed = true;
            require(allowed && used.add(slot), "invalid or duplicate slot");
            JsonNode schema = REGISTRY.path("assets").path(id).path("propsSchema").path("properties"), props = asset.path("props");
            require(props.isObject(), "props must be an object");
            props.fields().forEachRemaining(entry -> {
                String key = entry.getKey(); JsonNode v = entry.getValue();
                require(schema.has(key), "unknown asset prop");
                JsonNode rule = schema.path(key);
                boolean enumValid = !rule.has("enum");
                for (JsonNode option : rule.path("enum")) if (option.equals(v)) enumValid = true;
                boolean valid = switch (rule.path("type").asText()) {
                    case "string" -> v.isTextual() && v.asText().length() <= rule.path("maxLength").asInt(Integer.MAX_VALUE)
                        && (!rule.has("pattern") || v.asText().matches(rule.path("pattern").asText())) && enumValid;
                    case "number" -> v.isNumber() && Double.isFinite(v.asDouble())
                        && v.asDouble() >= rule.path("minimum").asDouble(Double.NEGATIVE_INFINITY)
                        && v.asDouble() <= rule.path("maximum").asDouble(Double.POSITIVE_INFINITY);
                    default -> false;
                };
                require(valid, "invalid asset prop " + key);
            });
        }
        require(used.contains("main"), "main slot required");
    }
}
