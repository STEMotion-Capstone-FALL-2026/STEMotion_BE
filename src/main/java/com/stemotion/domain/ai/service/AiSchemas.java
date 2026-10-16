package com.stemotion.domain.ai.service;

import java.util.List;
import java.util.Map;

/**
 * Response schemas handed to Gemini so generated JSON already matches the
 * front-end contract and needs no repair pass.
 */
final class AiSchemas {

    private AiSchemas() {}

    static final List<String> FLAT_AMBIENCES = List.of("space", "cell", "lab", "ocean", "lilac", "sky");
    static final List<String> FLAT_LAYOUTS = List.of("focus", "row", "cluster", "swarm", "exchange", "zoom");
    static final List<String> COMPOSED_FRAMES = List.of("center", "split", "thirds", "grid", "sidebar");
    static final List<String> COMPOSED_SLOTS =
        List.of("top", "main", "bottom", "left", "right", "middle", "tl", "tr", "bl", "br", "side");
    static final List<String> COMPOSED_KINDS = List.of(
        "title", "text", "list", "formula", "icon", "icons", "number", "chart", "choice", "arrow", "highlight", "guide");

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "STRING", "enum", values);
    }

    /** One block of a COMPOSED scene; which fields matter depends on its kind. */
    private static Map<String, Object> composedBlock() {
        Map<String, Object> icon = object(Map.of("name", str(), "label", str()), List.of("name"));
        Map<String, Object> point = object(Map.of("label", str(), "value", num()), List.of("label", "value"));
        return object(
            Map.ofEntries(
                Map.entry("id", str()),
                Map.entry("kind", enumOf(COMPOSED_KINDS)),
                Map.entry("slot", enumOf(COMPOSED_SLOTS)),
                Map.entry("text", str()),
                Map.entry("items", arrayOf(str())),
                Map.entry("latex", str()),
                Map.entry("icon", icon),
                Map.entry("icons", arrayOf(icon)),
                Map.entry("value", num()),
                Map.entry("unit", str()),
                Map.entry("points", arrayOf(point)),
                Map.entry("question", str()),
                Map.entry("options", arrayOf(str())),
                Map.entry("answer", num()),
                Map.entry("from", str()),
                Map.entry("to", str()),
                Map.entry("pose", enumOf(List.of("idle", "point"))),
                Map.entry("at", str()),
                Map.entry("until", str()),
                Map.entry("revealAt", str()),
                Map.entry("strikeAt", str()),
                Map.entry("motion", enumOf(List.of("pop", "rise", "slide", "draw", "type", "count"))),
                Map.entry("emphasis", enumOf(List.of("none", "pulse", "float", "shake"))),
                Map.entry("tone", enumOf(List.of("default", "accent", "good", "bad")))
            ),
            List.of("id", "kind")
        );
    }

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
                    // The registered template set first (proposal 3.2.c), the flat-explainer scenes last.
                    "TITLE_HERO", "MATH_FORMULA", "DIAGRAM_EXPLAINER", "DATA_CHART", "ALGORITHM_WALKTHROUGH",
                    "STEM_QUIZ", "OUTRO", "CHEMICAL_REACTION", "PROCESS_TIMELINE", "COMPARISON_SPLIT",
                    "ILLUSTRATED_EXPLAINER", "COMPOSED"
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
            Map.entry("ambience", Map.of("type", "STRING", "enum", FLAT_AMBIENCES)),
            Map.entry("layout", Map.of("type", "STRING", "enum", FLAT_LAYOUTS)),
            Map.entry("icons", arrayOf(flatIcon)),
            Map.entry("frame", enumOf(COMPOSED_FRAMES)),
            Map.entry("question", str()),
            Map.entry("options", arrayOf(str())),
            Map.entry("correctIndex", num()),
            Map.entry("hint", str()),
            Map.entry("explanation", str()),
            Map.entry("diagramTitle", str()),
            Map.entry("labels", arrayOf(object(
                Map.of("name", str(), "description", str(), "xPercent", num(), "yPercent", num()),
                List.of("name", "description")))),
            Map.entry("svgType", enumOf(List.of("pendulum", "atom", "circuit", "cell"))),
            Map.entry("language", str()),
            Map.entry("codeSnippet", str()),
            Map.entry("blocks", arrayOf(composedBlock()))
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
                "approachName", str(),
                "flow", arrayOf(str()),
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

    /**
     * metaphor: three alternative visual metaphors for one scene. Each carries a
     * ready ILLUSTRATED_EXPLAINER staging and a narration rewritten around it,
     * plus where the comparison stops being accurate.
     */
    static Map<String, Object> metaphor() {
        Map<String, Object> icon = object(Map.of("name", str(), "label", str()), List.of("name", "label"));
        Map<String, Object> option = object(
            Map.ofEntries(
                Map.entry("name", str()),
                Map.entry("idea", str()),
                Map.entry("whyItHelps", str()),
                Map.entry("limitation", str()),
                Map.entry("layout", Map.of("type", "STRING", "enum", FLAT_LAYOUTS)),
                Map.entry("ambience", Map.of("type", "STRING", "enum", FLAT_AMBIENCES)),
                Map.entry("headline", str()),
                Map.entry("caption", str()),
                Map.entry("icons", arrayOf(icon)),
                Map.entry("narration", str())
            ),
            List.of("name", "idea", "limitation", "layout", "ambience", "headline", "icons", "narration")
        );
        return object(Map.of("options", arrayOf(option)), List.of("options"));
    }

    /** Several distinct ways to lead one lesson, for the writer to choose from. */
    static Map<String, Object> approaches() {
        Map<String, Object> option = object(
            Map.of(
                "name", str(),
                "hook", str(),
                "flow", arrayOf(str()),
                "whyItFits", str()
            ),
            List.of("name", "hook", "flow", "whyItFits")
        );
        return object(
            Map.of(
                "isValidStemTopic", Map.of("type", "BOOLEAN"),
                "fallbackMessage", str(),
                "options", arrayOf(option)
            ),
            List.of("isValidStemTopic", "options")
        );
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
