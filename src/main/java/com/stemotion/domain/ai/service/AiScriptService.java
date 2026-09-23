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
            Bạn là một Chuyên gia Thiết kế Bài giảng STEM xuất sắc. Mục tiêu: Tạo kịch bản video giáo dục ngắn, sinh động, chuẩn xác khoa học, không dư thừa từ ngữ.

            BƯỚC 1: KIỂM DUYỆT ĐẦU VÀO (GUARDRAIL)
            - Topic người dùng: [%s] | Môn học: [%s] | Cấp độ: [%s]
            - Đánh giá: Topic này có vi phạm tiêu chuẩn (bạo lực, phản cảm, vô nghĩa) hoặc HOÀN TOÀN KHÔNG liên quan đến môn học/giáo dục STEM không? (VD: Topic "bún chả" cho môn Vật Lý).
            - NẾU KHÔNG HỢP LỆ:
              + Set "isValidStemTopic": false.
              + Set "fallbackMessage": Lời giải thích ngắn gọn, lịch sự bằng tiếng Việt (VD: "Chủ đề 'bún chả' không phù hợp với môn Vật Lý lớp 10. Vui lòng chọn chủ đề khoa học.").
              + BỎ QUA BƯỚC 2. Trả về mảng scenes rỗng để tiết kiệm token.

            BƯỚC 2: TẠO KỊCH BẢN (NẾU HỢP LỆ)
            - Set "isValidStemTopic": true. Set "fallbackMessage": null.
            - Tạo 5-7 cảnh (scenes). Tổng duration các cảnh phải xấp xỉ %d giây (durationInFrames = giây * 30).
            - Yêu cầu chất lượng khắt khe:
              + Cảnh 1 LUÔN dùng template TITLE_HERO.
              + Cảnh cuối LUÔN dùng template OUTRO.
              + Nội dung khoa học, công thức phải CHÍNH XÁC TUYỆT ĐỐI.
              + Lời thoại (narration) súc tích, nhịp độ nhanh (tối đa 2.2 từ/giây). Tuyệt đối không dùng từ ngữ sáo rỗng, vòng vo.

            YÊU CẦU ĐẶC THÙ CHO CÁC TEMPLATE STEM:
            - MATH_FORMULA: Cung cấp "latex" và "steps".
            - COMPARISON_SPLIT: Cung cấp "topicA", "topicB" và "conclusion".
            - STEM_QUIZ: Cung cấp "question", "options" (4 lựa chọn), "correctIndex" (0-3), "hint", "explanation".
            - DATA_CHART: Cung cấp "chartType" (bar/line), "xAxisLabel", "yAxisLabel", "dataPoints".
            - CHEMICAL_REACTION: Cung cấp "equation", "reactants", "products", "condition", "observation".
            - OUTRO: Cung cấp "summaryPoints" và "nextLessonSuggestion".
            """.formatted(request.topic(), request.subject(), request.gradeLevel(), targetDuration);

        AiActionResponse response = call(null, prompt, AiSchemas.generatedScript());
        
        // Guardrail Fallback Logic
        JsonNode result = response.result();
        if (result != null && result.has("isValidStemTopic") && !result.get("isValidStemTopic").asBoolean()) {
            String fallback = result.has("fallbackMessage") && !result.get("fallbackMessage").isNull() 
                ? result.get("fallbackMessage").asText() 
                : "Chủ đề bạn nhập không hợp lệ hoặc không liên quan đến giáo dục STEM.";
            throw com.stemotion.core.exception.ApiException.badRequest(fallback);
        }

        return response;
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
