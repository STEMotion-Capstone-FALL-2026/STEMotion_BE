package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.ai.dto.AiDtos.AiAction;
import com.stemotion.domain.ai.dto.AiDtos.AiActionResponse;
import com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest;
import com.stemotion.domain.ai.dto.AiDtos.ScriptActionRequest;
import com.stemotion.integration.client.AiGateway;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

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

    private final AiGateway geminiClient;
    private final IntegrationProperties properties;

    /** Drafts a complete script the writer can then edit scene by scene. */
    public AiActionResponse generateScript(GenerateScriptRequest request) {
        int rawDuration = request.targetDurationSec() == null ? 0 : request.targetDurationSec();
        String pacingInstruction;
        if (rawDuration <= 0) {
            pacingInstruction = """
                - TỰ ĐỘNG QUYẾT ĐỊNH SỐ CẢNH (DYNAMIC SCENE COUNT TỪ 4 ĐẾN 9 CẢNH) dựa vào độ rộng và độ sâu khoa học của Topic:
                  + Nếu Topic là 1 định nghĩa/công thức đơn lẻ, ngắn gọn -> Tạo đúng 4 hoặc 5 cảnh (tổng ~55-65 giây).
                  + Nếu Topic là một cơ chế, hiện tượng hoặc quy trình tự nhiên có nhiều bước -> Tạo 6 hoặc 7 cảnh (tổng ~85-105 giây).
                  + Nếu Topic là chủ đề vĩ mô, nhiều tầng kiến thức sâu sắc kiểu Kurzgesagt (VD: Hố đen, Hệ miễn dịch, Thuyết tương đối, Tiến hóa, Lượng tử...) -> Tạo 8 hoặc 9 cảnh (tổng ~115-145 giây).
                  + Tuyệt đối KHÔNG mặc định lúc nào cũng 5 cảnh; hãy phân tích độ phức tạp của [%s] để chọn số cảnh phù hợp nhất!
                """.formatted(request.topic());
        } else if (rawDuration <= 65) {
            pacingInstruction = "- Tạo 4-5 cảnh (scenes) cô đọng. Tổng duration các cảnh xấp xỉ %d giây (durationInFrames = giây * 30).".formatted(rawDuration);
        } else if (rawDuration <= 100) {
            pacingInstruction = "- Tạo 6-7 cảnh (scenes) tiêu chuẩn. Tổng duration các cảnh xấp xỉ %d giây (durationInFrames = giây * 30).".formatted(rawDuration);
        } else {
            pacingInstruction = "- Tạo 8-10 cảnh (scenes) chuyên sâu, phân tích đa chiều. Tổng duration các cảnh xấp xỉ %d giây (durationInFrames = giây * 30).".formatted(rawDuration);
        }

        String prompt = """
            Bạn là một Chuyên gia Thiết kế Bài giảng STEM xuất sắc. Mục tiêu: Tạo kịch bản video giáo dục ngắn, sinh động, chuẩn xác khoa học, không dư thừa từ ngữ.

            BƯỚC 1: KIỂM DUYỆT ĐẦU VÀO (GUARDRAIL)
            - Topic người dùng: [%s] | Môn học: [%s] | Cấp độ: [%s]
            - Đánh giá: Topic này có vi phạm tiêu chuẩn (bạo lực, phản cảm, vô nghĩa) hoặc HOÀN TOÀN KHÔNG liên quan đến môn học/giáo dục STEM không? (VD: Topic "bún chả" cho môn Vật Lý).
            - NẾU KHÔNG HỢP LỆ:
              + Set "isValidStemTopic": false.
              + Set "fallbackMessage": Lời giải thích ngắn gọn, lịch sự bằng tiếng Việt (VD: "Chủ đề 'bún chả' không phù hợp với môn Vật Lý lớp 10. Vui lòng chọn chủ đề khoa học.").
              + BỎ QUA BƯỚC 2. Trả về mảng scenes rỗng để tiết kiệm token.

            BƯỚC 2: TẠO KỊCH BẢN (NẾU HỢP LỆ) - PHONG CÁCH KURZGESAGT EXPLAINER:
            - Set "isValidStemTopic": true. Set "fallbackMessage": null.
            %s
            - Yêu cầu chất lượng khắt khe:
              + Cảnh 1 LUÔN dùng template TITLE_HERO.
              + Các cảnh giữa: ƯU TIÊN dùng nhiều cảnh ILLUSTRATED_EXPLAINER (xen kẽ các layout "focus", "row", "swarm", "cluster" và các ambience khác nhau) kết hợp PROCESS_TIMELINE, COMPARISON_SPLIT hoặc MATH_FORMULA / CHEMICAL_REACTION / DATA_CHART khi cần.
              + Cảnh cuối LUÔN dùng template OUTRO.
              + KHÔNG dùng STEM_QUIZ, DIAGRAM_EXPLAINER hay GEOMETRY_SPACE.
              + Nội dung khoa học, công thức phải CHÍNH XÁC TUYỆT ĐỐI.
              + Lời thoại (narration) súc tích, cuốn hút kiểu kể chuyện khoa học (tối đa 2.2 từ/giây).

            YÊU CẦU ĐẶC THÙ CHO CÁC TEMPLATE STEM:
            - ILLUSTRATED_EXPLAINER (Trọng tâm): dùng để hình dung trực quan (cấu tạo, vai trò, chuỗi nhân quả, đám mây hạt).
              Cung cấp "headline" (tối đa 8 từ), "caption" (một câu ngắn), "ambience" (space: vũ trụ/vật lý; cell: sinh học;
              lab: hóa học/thí nghiệm; ocean: nước/Trái Đất; lilac, sky: nền sáng pastel xen kẽ với nền tối),
              "layout" (focus: một khái niệm trung tâm và các thành phần quanh nó; row: quy trình theo thứ tự có mũi tên;
              cluster: nhóm khái niệm ngang hàng; swarm: tập hợp nhiều hạt như tế bào, nguyên tử, đám mây electron)
              và "icons" (3-5 biểu tượng, mỗi cái có "name" BẮT BUỘC lấy từ danh sách [%s] và "label" tiếng Việt tối đa 3 từ).
              Với layout focus và swarm, biểu tượng đầu tiên là tâm điểm.
            - PROCESS_TIMELINE: Cung cấp "processTitle" và "stages" (3-4 giai đoạn, mỗi cái có stageNumber, title, description).
            - COMPARISON_SPLIT: Cung cấp "topicA" (title, badge, points), "topicB" (title, badge, points) và "conclusion".
            - MATH_FORMULA: Cung cấp "latex" (bắt buộc không rỗng) và "steps".
            - CHEMICAL_REACTION: Cung cấp "equation" (bắt buộc không rỗng), "reactants", "products", "condition", "observation".
            - DATA_CHART: Cung cấp "chartType" (bar/line), "xAxisLabel", "yAxisLabel", "dataPoints" (mảng có label và value là số).
            - OUTRO: Cung cấp "summaryPoints" (3 ý đúc kết cốt lõi) và "nextLessonSuggestion".
            """.formatted(request.topic(), request.subject(), request.gradeLevel(), pacingInstruction, String.join(", ", StemIconCatalog.NAMES));

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
                Also suggest 2-4 icons per scene from the allowed icon names; the
                producer may swap them, so pick the ones that best picture the idea.

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
        String instruction = SYSTEM_INSTRUCTION;
        if (action == null || action == AiAction.resegment) {
            instruction += "\nFor icons[].name and suggestedIcons[], use only these exact names: "
                + String.join(", ", StemIconCatalog.NAMES) + ".";
        }
        var generation = geminiClient.generateJson(action==null || action==AiAction.resegment ? com.stemotion.domain.ai.entity.AiRequest.RequestType.SEGMENTATION : com.stemotion.domain.ai.entity.AiRequest.RequestType.ANALYSIS,instruction, prompt, schema);
        JsonNode result = generation.result();
        validateIconNames(result);
        long elapsed = System.currentTimeMillis() - startedAt;

        if (elapsed > 15_000) {
            log.warn("AI action {} took {} ms, over the 15s target", action, elapsed);
        }

        return new AiActionResponse(action, result, generation.model(), elapsed, generation.requestId());
    }

    private void validateIconNames(JsonNode result) {
        if (result == null) return;
        for (JsonNode scene : result.path("scenes")) {
            for (JsonNode icon : scene.path("icons")) {
                requireKnownIcon(icon.path("name"));
            }
            for (JsonNode icon : scene.path("suggestedIcons")) {
                requireKnownIcon(icon);
            }
        }
    }

    private void requireKnownIcon(JsonNode name) {
        if (!name.isTextual() || !StemIconCatalog.NAMES.contains(name.asText())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE",
                "AI returned an unsupported icon. Please generate the script again.");
        }
    }
}
