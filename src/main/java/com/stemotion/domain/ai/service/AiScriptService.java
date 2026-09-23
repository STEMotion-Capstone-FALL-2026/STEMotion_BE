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
        You are a senior Vietnamese STEM educational curriculum specialist and video scriptwriter for Vietnamese secondary and high schools (GDPT 2018).

        MANDATORY LANGUAGE & ORTHOGRAPHY RULES:
        1. ALL textual content — including narration (lời thoại thuyết minh), titles (tiêu đề phân cảnh), questions (câu hỏi trắc nghiệm), options (đáp án), hints (gợi ý), explanations (giải thích), and summaries — MUST BE 100% WRITTEN IN PROPER STANDARD VIETNAMESE WITH FULL DIACRITICS, ACCENTS, AND TONE MARKS (Tiếng Việt có dấu thanh và dấu mũ đầy đủ).
        2. NEVER EVER write unaccented Vietnamese (TUYỆT ĐỐI KHÔNG VIẾT TIẾNG VIỆT KHÔNG DẤU, KHÔNG ĐƯỢC VIẾT "Chao mung cac em...", BẮT BUỘC PHẢI VIẾT "Chào mừng các em...").
        3. Use natural, engaging, pedagogical Vietnamese suitable for classroom lectures and educational animations.
        4. Keep scientific terminology standard according to Vietnam's Ministry of Education (GDPT 2018).
        5. Respond ONLY with valid JSON strictly conforming to the provided schema.
        """;

    private final GeminiClient geminiClient;
    private final IntegrationProperties properties;

    /** Drafts a complete script the writer can then edit scene by scene. */
    public AiActionResponse generateScript(GenerateScriptRequest request) {
        int targetDuration = request.targetDurationSec() == null ? 90 : request.targetDurationSec();

        String prompt = """
            Soạn kịch bản video bài giảng STEM chi tiết bằng TIẾNG VIỆT CÓ DẤU ĐẦY ĐỦ (Standard Vietnamese with diacritics).

            Thông tin đề bài:
            - Chủ đề (Topic): %s
            - Môn học (Subject): %s
            - Khối lớp (Grade level): %s
            - Thời lượng mục tiêu: %d giây (ở tốc độ 30 khung hình/giây - 30 fps)

            QUY TẮC BẮT BUỘC VỀ NGÔN NGỮ (LANGUAGE RULES):
            - TẤT CẢ các trường văn bản (title, narration, question, options, hint, explanation, summaryPoints, topicA, topicB,...) BẮT BUỘC PHẢI VIẾT BẰNG TIẾNG VIỆT CÓ ĐẦY ĐỦ DẤU THANH (sắc, huyền, hỏi, ngã, nặng) và DẤU MŨ (ă, â, đ, ê, ô, ơ, ư).
            - TUYỆT ĐỐI KHÔNG VIẾT TIẾNG VIỆT KHÔNG DẤU (Ví dụ: phải viết "Chào mừng các em đến với bài học...", TUYỆT ĐỐI KHÔNG VIẾT "Chao mung cac em den voi bai hoc...").
            - Lời thoại (narration) cần truyền cảm, tự nhiên, sư phạm, giải thích rõ ràng từng hiện tượng và công thức để học sinh dễ hiểu và hứng thú học tập.

            Cấu trúc phân cảnh:
            - Tạo từ 5 đến 7 phân cảnh. Mở đầu bằng TITLE_HERO và kết thúc bằng OUTRO.
            - Chọn các loại phân cảnh ở giữa phù hợp nhất với môn học và chủ đề:
              + MATH_FORMULA: giải thích công thức, biến đổi toán học/vật lý.
              + DIAGRAM_EXPLAINER: phân tích cấu trúc, mô hình trực quan.
              + DATA_CHART: đồ thị, dữ liệu thực nghiệm, thống kê.
              + ALGORITHM_WALKTHROUGH: giải thích thuật toán, dòng chảy chương trình.
              + STEM_QUIZ: câu hỏi tương tác trắc nghiệm củng cố kiến thức.
              + CHEMICAL_REACTION: phương trình phản ứng hóa học, chất phản ứng, sản phẩm, điều kiện, hiện tượng.
              + COMPARISON_SPLIT: so sánh đối chiếu giữa 2 khái niệm hoặc 2 hiện tượng.
              + PROCESS_TIMELINE: các giai đoạn của quy trình hoặc chu trình sinh học/địa chất/công nghệ.
              + GEOMETRY_SPACE: hình học không gian, tọa độ, mô hình không gian 3D.
            - durationInFrames = số giây của phân cảnh * 30. Tổng thời lượng các phân cảnh xấp xỉ thời lượng mục tiêu.

            YÊU CẦU CHI TIẾT THEO TỪNG LOẠI PHÂN CẢNH:
            - MATH_FORMULA: Cung cấp "latex" công thức cốt lõi (ví dụ W = \\\\frac{1}{2}mv^2 + mgh) và danh sách "steps" (label, latexSnippet, explanation có dấu).
            - COMPARISON_SPLIT: Cung cấp "topicA" ({title, badge, points}), "topicB" ({title, badge, points}), và "conclusion" bằng tiếng Việt có dấu.
            - STEM_QUIZ: Cung cấp "question", "options" (4 lựa chọn tiếng Việt có dấu), "correctIndex" (0-3), "hint", và "explanation" chi tiết.
            - DATA_CHART: Cung cấp "chartType" ("bar" hoặc "line"), "xAxisLabel", "yAxisLabel", và "dataPoints" sát thực tế.
            - CHEMICAL_REACTION: Cung cấp "equation" (ví dụ Cu + 4HNO3 -> Cu(NO3)2 + 2NO2 + 2H2O), "reactants", "products", "condition", "observation" (mô tả hiện tượng màu sắc, kết tủa, khí thoát ra bằng tiếng Việt có dấu).
            - OUTRO: Cung cấp "summaryPoints" (tổng kết kiến thức trọng tâm bằng tiếng Việt có dấu) và "nextLessonSuggestion".
            - Tất cả công thức LaTeX phải escape dấu backslash hợp lệ (dùng \\\\\\\\) và không để sót dấu backslash ở cuối chuỗi.
            """.formatted(request.topic(), request.subject(), request.gradeLevel(), targetDuration);

        return call(null, prompt, AiSchemas.generatedScript());
    }

    /** Runs one of the four on-demand analyses over an existing script. */
    public AiActionResponse runAction(ScriptActionRequest request) {
        String subject = request.subject() == null ? "STEM" : request.subject().name();
        String grade = request.gradeLevel() == null ? "trung học" : request.gradeLevel();

        String prompt = switch (request.action()) {
            case resegment -> """
                Phân đoạn lại kịch bản môn %s dành cho học sinh %s thành 5-7 phân cảnh liền mạch.
                BẮT BUỘC: Toàn bộ tiêu đề và lời thoại phải bằng TIẾNG VIỆT CÓ ĐẦY ĐỦ DẤU THANH (có dấu chuẩn chính tả, tuyệt đối không viết không dấu).
                Với mỗi phân cảnh, chọn template phù hợp nhất và thời lượng tính bằng giây tương ứng với độ dài lời thoại.

                Kịch bản gốc:
                %s
                """.formatted(subject, grade, request.scriptText());

            case grade -> """
                Đánh giá mức độ phù hợp về mặt sư phạm và khả năng tiếp thu của kịch bản môn %s đối với học sinh %s.
                BẮT BUỘC: Toàn bộ phản hồi, nhận xét, gợi ý phải bằng TIẾNG VIỆT CÓ ĐẦY ĐỦ DẤU THANH (có dấu chuẩn chính tả).
                Cho điểm độ đọc hiểu từ 0 đến 100, xác định trình độ thực tế và đưa ra các đề xuất cải thiện cụ thể.

                Kịch bản:
                %s
                """.formatted(subject, grade, request.scriptText());

            case extract -> """
                Trích xuất danh sách các khái niệm STEM được giảng dạy trong kịch bản môn %s dành cho học sinh %s.
                BẮT BUỘC: Toàn bộ tên khái niệm, phân loại, giải thích phải bằng TIẾNG VIỆT CÓ ĐẦY ĐỦ DẤU THANH.
                Gán cho mỗi khái niệm một danh mục ngắn gọn và mức độ trọng tâm (core/secondary/enrichment).

                Kịch bản:
                %s
                """.formatted(subject, grade, request.scriptText());

            case terms -> """
                Rà soát thuật ngữ khoa học trong kịch bản môn %s dành cho học sinh %s.
                BẮT BUỘC: Đánh giá bằng TIẾNG VIỆT CÓ ĐẦY ĐỦ DẤU THANH.
                Chỉ cảnh báo các thuật ngữ bị dùng sai, không nhất quán giữa các phân cảnh, hoặc quá khó so với lứa tuổi. Không cảnh báo thuật ngữ đã chính xác.

                Kịch bản:
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
