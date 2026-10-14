package com.stemotion.domain.script.mapper;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.ai.service.StemIconCatalog;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a COMPOSED scene: a frame plus blocks the renderer assembles freely.
 *
 * <p>The freedom is in how blocks are combined, not in what a block may be:
 * every block must be one of the kinds STEMotion_FE/src/remotion/composed
 * can draw and carry the content that kind needs, so an AI draft or a manual
 * edit can never produce a scene that fails late in the render service.
 * Keep the lists in sync with the ComposedBlock type in
 * STEMotion_FE/src/types/stem.ts.
 */
final class ComposedSceneValidator {

    private ComposedSceneValidator() {}

    static final List<String> FRAMES = List.of("center", "split", "thirds", "grid", "sidebar");
    static final List<String> SLOTS =
        List.of("top", "main", "bottom", "left", "right", "middle", "tl", "tr", "bl", "br", "side");
    static final List<String> KINDS = List.of(
        "title", "text", "list", "formula", "icon", "icons", "number",
        "chart", "choice", "arrow", "highlight", "guide"
    );
    private static final List<String> MOTIONS = List.of("pop", "rise", "slide", "draw", "type", "count");
    private static final List<String> EMPHASES = List.of("none", "pulse", "float", "shake");
    private static final List<String> TONES = List.of("default", "accent", "good", "bad");
    private static final List<String> POSES = List.of("idle", "point");
    private static final List<String> AMBIENCES = List.of("space", "cell", "lab", "ocean", "lilac", "sky");

    static final int MAX_BLOCKS = 12;
    private static final int MAX_TEXT = 220;

    static void validate(Map<String, Object> props) {
        oneOf(props.get("frame"), FRAMES, "frame", true);
        oneOf(props.get("ambience"), AMBIENCES, "ambience", false);
        if (!(props.get("blocks") instanceof List<?> blocks) || blocks.isEmpty() || blocks.size() > MAX_BLOCKS) {
            throw bad("blocks must be a list of 1 to " + MAX_BLOCKS + " blocks");
        }

        Set<String> ids = new HashSet<>();
        for (Object item : blocks) {
            if (!(item instanceof Map<?, ?> block)) throw bad("every block must be an object");
            Object id = block.get("id");
            if (!(id instanceof String s) || s.isBlank()) throw bad("every block needs an id");
            if (!ids.add(s)) throw bad("block id '" + s + "' is used twice");
        }

        for (Object item : blocks) {
            Map<?, ?> block = (Map<?, ?>) item;
            String id = (String) block.get("id");
            Object kind = block.get("kind");
            if (!(kind instanceof String k) || !KINDS.contains(k)) {
                throw bad("block " + id + " kind must be one of " + String.join(", ", KINDS));
            }
            oneOf(block.get("slot"), SLOTS, "block " + id + " slot", false);
            oneOf(block.get("motion"), MOTIONS, "block " + id + " motion", false);
            oneOf(block.get("emphasis"), EMPHASES, "block " + id + " emphasis", false);
            oneOf(block.get("tone"), TONES, "block " + id + " tone", false);

            switch (k) {
                case "title", "text" -> text(block, "text", id, true);
                case "list" -> strings(block, "items", id, 1, 6);
                case "formula" -> text(block, "latex", id, true);
                case "icon" -> icon(block.get("icon"), id);
                case "icons" -> {
                    if (!(block.get("icons") instanceof List<?> icons) || icons.isEmpty() || icons.size() > 6) {
                        throw bad("block " + id + " icons must hold 1 to 6 icons");
                    }
                    icons.forEach(icon -> icon(icon, id));
                }
                case "number" -> {
                    if (!(block.get("value") instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                        throw bad("block " + id + " value must be a number");
                    }
                }
                case "chart" -> {
                    if (!(block.get("points") instanceof List<?> points) || points.isEmpty() || points.size() > 8) {
                        throw bad("block " + id + " points must hold 1 to 8 points");
                    }
                    for (Object p : points) {
                        if (!(p instanceof Map<?, ?> point) || !(point.get("label") instanceof String)
                            || !(point.get("value") instanceof Number v) || !Double.isFinite(v.doubleValue())) {
                            throw bad("block " + id + " points need a label and a numeric value");
                        }
                    }
                }
                case "choice" -> {
                    int count = strings(block, "options", id, 2, 4);
                    if (!(block.get("answer") instanceof Number answer)
                        || answer.intValue() < 0 || answer.intValue() >= count) {
                        throw bad("block " + id + " answer must point at one of its options");
                    }
                }
                case "arrow" -> {
                    reference(block.get("from"), ids, id, "from");
                    reference(block.get("to"), ids, id, "to");
                }
                case "highlight" -> reference(block.get("to"), ids, id, "to");
                case "guide" -> {
                    oneOf(block.get("pose"), POSES, "block " + id + " pose", false);
                    text(block, "text", id, false);
                }
                default -> { /* every kind is handled above */ }
            }
        }
    }

    private static void text(Map<?, ?> block, String key, String id, boolean required) {
        Object value = block.get(key);
        if (value == null && !required) return;
        if (!(value instanceof String s) || s.isBlank()) throw bad("block " + id + " needs " + key);
        if (s.length() > MAX_TEXT) throw bad("block " + id + " " + key + " is longer than " + MAX_TEXT + " characters");
    }

    private static int strings(Map<?, ?> block, String key, String id, int min, int max) {
        if (!(block.get(key) instanceof List<?> list) || list.size() < min || list.size() > max) {
            throw bad("block " + id + " " + key + " must hold " + min + " to " + max + " entries");
        }
        for (Object entry : list) {
            if (!(entry instanceof String s) || s.isBlank() || s.length() > MAX_TEXT) {
                throw bad("block " + id + " " + key + " entries must be short, non-empty text");
            }
        }
        return list.size();
    }

    /** Unknown icon names fall back to the sparkle, as in ILLUSTRATED_EXPLAINER. */
    @SuppressWarnings("unchecked")
    private static void icon(Object icon, String id) {
        if (!(icon instanceof Map<?, ?> m) || !(m.get("name") instanceof String name) || name.isBlank()) {
            throw bad("block " + id + " icons need a name");
        }
        if (!StemIconCatalog.isKnown(name)) {
            try {
                ((Map<String, Object>) m).put("name", "sparkle");
            } catch (UnsupportedOperationException ignored) {
                // Immutable map: the renderer falls back to its own default icon.
            }
        }
    }

    private static void reference(Object target, Set<String> ids, String id, String key) {
        if (!(target instanceof String t) || !ids.contains(t)) {
            throw bad("block " + id + " " + key + " must name another block of this scene");
        }
        if (t.equals(id)) throw bad("block " + id + " cannot point at itself");
    }

    private static void oneOf(Object value, List<String> allowed, String what, boolean required) {
        if (value == null) {
            if (required) throw bad(what + " is required");
            return;
        }
        if (!allowed.contains(String.valueOf(value))) {
            throw bad(what + " must be one of " + String.join(", ", allowed));
        }
    }

    private static ApiException bad(String message) {
        return ApiException.badRequest("COMPOSED " + message);
    }
}
