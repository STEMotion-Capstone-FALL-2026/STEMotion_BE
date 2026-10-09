package com.stemotion.domain.ai.service;

import java.util.List;
import java.util.Set;

/**
 * The approved STEM icon names for ILLUSTRATED_EXPLAINER scenes.
 *
 * <p>Mirrors STEM_ICONS in STEMotion_FE/src/remotion/flat/stemIcons.ts, where
 * each name is bound to a Phosphor icon. The list is handed to Gemini as an
 * enum, so the AI can only suggest icons the renderer can draw, and the scene
 * validator rejects any other name.
 */
public final class StemIconCatalog {

    private StemIconCatalog() {}

    public static final List<String> NAMES = List.of(
        "atom", "lightning", "magnet", "wave", "thermometer", "rocket", "planet", "sun", "moon",
        "star", "shooting-star", "gauge", "scales", "radioactive", "lightbulb", "plug", "solar-panel",
        "windmill", "waveform", "speaker", "compass", "flask", "test-tube", "eyedropper", "fire",
        "drop", "solution", "snowflake", "hexagon", "recycle", "dna", "virus", "bug", "microscope",
        "leaf", "plant", "tree", "flower", "heart", "heartbeat", "brain", "bone", "tooth",
        "eye", "ear", "pill", "syringe", "fish", "bird", "butterfly", "animal", "person", "grains",
        "vegetable", "globe", "cloud", "rain", "wind", "tornado", "hurricane", "mountains",
        "ocean", "math-operations", "function", "calculator", "ruler", "triangle", "circle",
        "square", "cube", "sphere", "cylinder", "infinity", "pi", "sigma", "percent", "chart-line",
        "chart-bar", "chart-pie", "plus-minus", "equals", "cpu", "code", "terminal", "database",
        "binary", "robot", "laptop", "tree-structure", "flow", "network", "lock", "key", "gear",
        "wrench", "hammer", "engine", "car", "airplane", "train", "factory", "battery", "clock",
        "hourglass", "question", "target", "trophy", "book", "student", "sparkle"
    );

    private static final Set<String> NAME_SET = Set.copyOf(NAMES);

    public static boolean isKnown(String name) {
        return name != null && NAME_SET.contains(name.trim().toLowerCase());
    }
}
