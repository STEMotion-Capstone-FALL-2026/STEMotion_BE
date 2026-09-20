package com.stemotion.domain.ai.service;

import java.util.List;
import java.util.Map;

/**
 * Response schemas handed to Gemini so generated JSON already matches the
 * front-end contract and needs no repair pass.
 */
final class AiSchemas {

    private AiSchemas() {}

    private static Map<String, Object> str() {
        return Map.of("type", "STRING");
    }

    private static Map<String, Object> num() {
        return Map.of("type", "NUMBER");
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        return Map.of("type", "ARRAY", "items", items);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of(
            "type", "OBJECT",
            "properties", properties,
            "required", required
        );
    }

    /** A full draft script: metadata plus an ordered scene list. */
    static Map<String, Object> generatedScript() {
        Map<String, Object> scene = object(
            Map.of(
                "type", Map.of(
                    "type", "STRING",
                    "enum", List.of(
                        "TITLE_HERO", "MATH_FORMULA", "DIAGRAM_EXPLAINER", "DATA_CHART",
                        "ALGORITHM_WALKTHROUGH", "STEM_QUIZ", "OUTRO",
                        "CHEMICAL_REACTION", "COMPARISON_SPLIT", "PROCESS_TIMELINE", "GEOMETRY_SPACE"
                    )
                ),
                "title", str(),
                "narration", str(),
                "durationInFrames", num()
            ),
            List.of("type", "title", "narration", "durationInFrames")
        );

        return object(
            Map.of(
                "title", str(),
                "gradeLevel", str(),
                "totalDurationSeconds", num(),
                "scenes", arrayOf(scene)
            ),
            List.of("title", "scenes")
        );
    }

    /** resegment: scene boundaries with a suggested template per segment. */
    static Map<String, Object> resegment() {
        Map<String, Object> segment = object(
            Map.of(
                "type", str(),
                "title", str(),
                "narration", str(),
                "suggestedDurationSec", num(),
                "concepts", arrayOf(str())
            ),
            List.of("type", "title", "narration", "suggestedDurationSec")
        );
        return object(Map.of("scenes", arrayOf(segment)), List.of("scenes"));
    }

    /** grade: a readability verdict against the target grade. */
    static Map<String, Object> grade() {
        return object(
            Map.of(
                "readabilityScore", num(),
                "estimatedGrade", str(),
                "matchesTargetGrade", Map.of("type", "BOOLEAN"),
                "verdict", str(),
                "suggestions", arrayOf(str())
            ),
            List.of("readabilityScore", "estimatedGrade", "verdict")
        );
    }

    /** extract: STEM concept tags found in the script. */
    static Map<String, Object> extract() {
        Map<String, Object> concept = object(
            Map.of(
                "name", str(),
                "category", str(),
                "importance", str()
            ),
            List.of("name")
        );
        return object(Map.of("concepts", arrayOf(concept)), List.of("concepts"));
    }

    /** terms: terminology and consistency problems for human review. */
    static Map<String, Object> terms() {
        Map<String, Object> issue = object(
            Map.of(
                "term", str(),
                "issue", str(),
                "severity", Map.of("type", "STRING", "enum", List.of("LOW", "MEDIUM", "HIGH")),
                "suggestion", str()
            ),
            List.of("term", "issue", "severity")
        );
        return object(Map.of("issues", arrayOf(issue)), List.of("issues"));
    }
}
