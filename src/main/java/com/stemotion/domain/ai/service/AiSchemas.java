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
        Map<String, Object> stepItem = object(
            Map.of(
                "label", str(),
                "latexSnippet", str(),
                "explanation", str()
            ),
            List.of("label", "latexSnippet", "explanation")
        );

        Map<String, Object> topicSide = object(
            Map.of(
                "title", str(),
                "badge", str(),
                "points", arrayOf(str())
            ),
            List.of("title", "points")
        );

        Map<String, Object> dataPoint = object(
            Map.of(
                "label", str(),
                "value", num()
            ),
            List.of("label", "value")
        );

        // A large icon enum makes Gemini reject this nested schema with HTTP 400.
        // Supply the catalog in the instruction and validate generated names locally.
        Map<String, Object> flatIcon = object(
            Map.of(
                "name", str(),
                "label", str()
            ),
            List.of("name", "label")
        );

        Map<String, Object> stageItem = object(
            Map.of(
                "stageNumber", num(),
                "title", str(),
                "description", str()
            ),
            List.of("stageNumber", "title", "description")
        );

        Map<String, Object> sceneProps = Map.ofEntries(
            Map.entry("type", Map.of(
                "type", "STRING",
                "enum", List.of(
                    "TITLE_HERO", "ILLUSTRATED_EXPLAINER", "PROCESS_TIMELINE", "COMPARISON_SPLIT",
                    "MATH_FORMULA", "CHEMICAL_REACTION", "DATA_CHART", "ALGORITHM_WALKTHROUGH", "OUTRO"
                )
            )),
            Map.entry("title", str()),
            Map.entry("narration", str()),
            Map.entry("durationInFrames", num()),
            Map.entry("latex", str()),
            Map.entry("steps", arrayOf(stepItem)),
            Map.entry("topicA", topicSide),
            Map.entry("topicB", topicSide),
            Map.entry("conclusion", str()),
            Map.entry("processTitle", str()),
            Map.entry("stages", arrayOf(stageItem)),
            Map.entry("chartType", str()),
            Map.entry("xAxisLabel", str()),
            Map.entry("yAxisLabel", str()),
            Map.entry("dataPoints", arrayOf(dataPoint)),
            Map.entry("equation", str()),
            Map.entry("reactants", str()),
            Map.entry("products", str()),
            Map.entry("condition", str()),
            Map.entry("observation", str()),
            Map.entry("summaryPoints", arrayOf(str())),
            Map.entry("nextLessonSuggestion", str()),
            Map.entry("headline", str()),
            Map.entry("caption", str()),
            Map.entry("ambience", Map.of("type", "STRING", "enum",
                List.of("space", "cell", "lab", "ocean", "lilac", "sky"))),
            Map.entry("layout", Map.of("type", "STRING", "enum", List.of("focus", "row", "cluster", "swarm"))),
            Map.entry("icons", arrayOf(flatIcon))
        );

        Map<String, Object> scene = object(
            sceneProps,
            List.of("type", "title", "narration", "durationInFrames")
        );

        return object(
            Map.of(
                "isValidStemTopic", Map.of("type", "BOOLEAN"),
                "fallbackMessage", Map.of("type", "STRING", "nullable", true),
                "title", str(),
                "gradeLevel", str(),
                "totalDurationSeconds", num(),
                "scenes", arrayOf(scene)
            ),
            List.of("isValidStemTopic", "scenes")
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
                "concepts", arrayOf(str()),
                "suggestedIcons", arrayOf(str())
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
