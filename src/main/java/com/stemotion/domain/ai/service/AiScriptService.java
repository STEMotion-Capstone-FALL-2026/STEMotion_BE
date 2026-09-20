package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.domain.ai.dto.AiDtos.AiAction;
import com.stemotion.domain.ai.dto.AiDtos.AiActionResponse;
import com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest;
import com.stemotion.domain.ai.dto.AiDtos.ScriptActionRequest;
import com.stemotion.integration.client.GeminiClient;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI Script Intelligence. Everything here is decision support: results are
 * returned to the writer, who accepts or ignores them. Nothing is written to a
 * script without an explicit save from the user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiScriptService {

    private static final String SYSTEM_INSTRUCTION = """
        You are a Vietnamese STEM curriculum assistant for secondary and high school.
        Write narration in Vietnamese, keep scientific terminology accurate and
        consistent, and match the vocabulary to the requested grade level.
        Respond only with JSON that satisfies the provided schema.
        """;

    private final GeminiClient geminiClient;
    private final IntegrationProperties properties;

    /** Drafts a complete script the writer can then edit scene by scene. */
    public AiActionResponse generateScript(GenerateScriptRequest request) {
        int targetDuration = request.targetDurationSec() == null ? 90 : request.targetDurationSec();

        String prompt = """
            Draft a STEM explainer video script in Vietnamese.

            Topic: %s
            Subject: %s
            Grade level: %s
            Target total duration: %d seconds at 30 fps

            Produce between 5 and 7 scenes. Open with TITLE_HERO and close with OUTRO.
            Choose the middle scene types to fit the subject: MATH_FORMULA for derivations,
            DIAGRAM_EXPLAINER for structures, DATA_CHART for measurements,
            ALGORITHM_WALKTHROUGH for code, STEM_QUIZ to check understanding,
            CHEMICAL_REACTION for chemical equations, COMPARISON_SPLIT to contrast
            two ideas, PROCESS_TIMELINE for staged processes, GEOMETRY_SPACE for
            geometry theorems.
            durationInFrames must be 30 times the intended seconds, and the scene
            durations must add up to roughly the target duration.

            CRITICAL REQUIREMENT - Provide accurate scientific details matching the Topic:
            - For MATH_FORMULA: MUST provide "latex" with the core formula for this topic (e.g. W = \\frac{1}{2}mv^2 + mgh, n_1 \\sin(i) = n_2 \\sin(r), etc.), and "steps" breakdown.
            - For COMPARISON_SPLIT: MUST provide "topicA" ({title, badge, points}), "topicB" ({title, badge, points}), and "conclusion" comparing two concepts from this topic.
            - For STEM_QUIZ: MUST provide "question", "options" (4 choices), "correctIndex" (0-3), "hint", and "explanation" directly testing knowledge from this topic.
            - For DATA_CHART: MUST provide "chartType" ("bar" or "line"), "xAxisLabel", "yAxisLabel", and "dataPoints" with realistic values.
            - For CHEMICAL_REACTION: MUST provide "equation", "reactants", "products", "condition", and "observation".
            - For OUTRO: MUST provide "summaryPoints" (key takeaways) and "nextLessonSuggestion".
            """.formatted(request.topic(), request.subject(), request.gradeLevel(), targetDuration);

        return call(null, prompt, AiSchemas.generatedScript());
    }

    /** Runs one of the four on-demand analyses over an existing script. */
    public AiActionResponse runAction(ScriptActionRequest request) {
        String subject = request.subject() == null ? "STEM" : request.subject().name();
        String grade = request.gradeLevel() == null ? "trung học" : request.gradeLevel();

        String prompt = switch (request.action()) {
            case resegment -> """
                Re-cut the following %s script for %s into 5-7 coherent scenes.
                For each scene choose the most suitable template type and a duration
                in seconds proportional to how much the narration covers.

                Script:
                %s
                """.formatted(subject, grade, request.scriptText());

            case grade -> """
                Assess whether the following %s script is readable for students at %s.
                Give a readability score from 0 to 100, the grade level the text
                actually reads at, and concrete suggestions to close any gap.

                Script:
                %s
                """.formatted(subject, grade, request.scriptText());

            case extract -> """
                List the %s concepts taught in the following script for %s.
                Give each concept a short category and mark how central it is.

                Script:
                %s
                """.formatted(subject, grade, request.scriptText());

            case terms -> """
                Review the following %s script for %s and flag terminology that is
                wrong, inconsistent between scenes, or above the expected level.
                Do not flag correct terminology.

                Script:
                %s
                """.formatted(subject, grade, request.scriptText());
        };

        Map<String, Object> schema = switch (request.action()) {
            case resegment -> AiSchemas.resegment();
            case grade -> AiSchemas.grade();
            case extract -> AiSchemas.extract();
            case terms -> AiSchemas.terms();
        };

        return call(request.action(), prompt, schema);
    }

    public boolean isConfigured() {
        return geminiClient.isConfigured();
    }

    private AiActionResponse call(AiAction action, String prompt, Map<String, Object> schema) {
        long startedAt = System.currentTimeMillis();
        JsonNode result = geminiClient.generateJson(SYSTEM_INSTRUCTION, prompt, schema);
        long elapsed = System.currentTimeMillis() - startedAt;

        if (elapsed > 15_000) {
            log.warn("AI action {} took {} ms, over the 15s target", action, elapsed);
        }

        return new AiActionResponse(action, result, properties.getGemini().getModel(), elapsed);
    }
}
