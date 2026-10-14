package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stemotion.domain.ai.dto.AiDtos.AiAction;
import com.stemotion.domain.ai.dto.AiDtos.AiActionResponse;
import com.stemotion.domain.ai.dto.AiDtos.ApproachRequest;
import com.stemotion.domain.ai.entity.AiRequest;
import com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest;
import com.stemotion.domain.ai.dto.AiDtos.ScriptActionRequest;
import com.stemotion.integration.client.AiGateway;
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

    /** Shown in place of an icon the renderer does not know. */
    private static final String FALLBACK_ICON = "sparkle";

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

        String approach = request.approach() == null ? "" : request.approach().strip();
        String approachInstruction = approach.isEmpty()
            ? """
                - CÁCH DẪN DẮT: tự sáng tạo MỘT cách dẫn dắt hợp nhất với bài này. Không có khuôn mẫu bắt buộc:
                  có thể bắt đầu bằng câu hỏi, thí nghiệm, một hiểu lầm phổ biến, ứng dụng đời sống, lịch sử khám phá,
                  bài toán mẫu, phép ví von, so sánh, trò chơi đố vui, cuộc điều tra, phỏng vấn một nhân vật khoa học...
                  hoặc một cách hoàn toàn khác. Ghi tên cách đó vào "approachName" và các bước của mạch vào "flow".
                """
            : """
                - CÁCH DẪN DẮT DO NGƯỜI VIẾT CHỌN (bắt buộc theo, dựng TOÀN BỘ mạch video quanh ý này):
                  «%s»
                  Ghi tên cách dẫn dắt vào "approachName" và các bước của mạch vào "flow".
                """.formatted(approach);

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
            %s
            - Yêu cầu chất lượng khắt khe:
              + Cảnh mở đầu phục vụ cách dẫn dắt: một câu hỏi, một tình huống, một con số gây tò mò (dùng COMPOSED) hoặc TITLE_HERO.
              + Các cảnh giữa: ƯU TIÊN COMPOSED để hình thức mỗi cảnh đi theo đúng mạch dẫn dắt; xen ILLUSTRATED_EXPLAINER
                (layout "focus", "row", "swarm", "cluster", "exchange", "zoom") khi cần minh hoạ, và PROCESS_TIMELINE,
                COMPARISON_SPLIT, MATH_FORMULA, CHEMICAL_REACTION, DATA_CHART khi nội dung khớp đúng khuôn đó.
              + Mỗi cảnh ILLUSTRATED_EXPLAINER nên dựng quanh MỘT ẨN DỤ HÌNH ẢNH cụ thể (VD: electron là quả bóng được chuyền, tế bào là nhà máy, hệ miễn dịch là đội quân) thay vì liệt kê khái niệm; ẩn dụ phải đúng bản chất khoa học.
              + Cảnh cuối LUÔN dùng template OUTRO.
              + Hai cảnh liền nhau không nên giống nhau về hình thức (đổi frame, đổi loại khối).
              + KHÔNG dùng STEM_QUIZ, DIAGRAM_EXPLAINER hay GEOMETRY_SPACE.
              + Nội dung khoa học, công thức phải CHÍNH XÁC TUYỆT ĐỐI.
              + Lời thoại (narration) súc tích, cuốn hút kiểu kể chuyện khoa học (tối đa 2.2 từ/giây).

            YÊU CẦU ĐẶC THÙ CHO CÁC TEMPLATE STEM:
            - COMPOSED (cảnh lắp ghép, tự do hình thức): cung cấp "frame" và "blocks" (2-6 khối).
              "frame" chia màn hình thành các ô không chồng nhau: center (top, main, bottom); split (top, left, right, bottom);
              thirds (top, left, middle, right, bottom); grid (top, tl, tr, bl, br); sidebar (top, main, side).
              Mỗi khối có "id" (ngắn, duy nhất trong cảnh), "kind", "slot" (một ô của frame) và nội dung theo loại:
              title/text: "text" (tối đa 12 từ cho title, 25 từ cho text); list: "items" (2-5 ý ngắn);
              formula: "latex" (đúng cú pháp KaTeX); icon: "icon" {name, label}; icons: "icons" (2-5);
              number: "value" (số), "unit", "text" (nhãn); chart: "points" [{label, value}] và "unit";
              choice: "question", "options" (2-4), "answer" (chỉ số đáp án đúng, đếm từ 0), "revealAt";
              guide: nhân vật dẫn chuyện, "pose" (idle/point) và "text" (lời thoại ngắn trong bong bóng);
              arrow: "from" và "to" là id của hai khối khác (không cần slot); highlight: "to" là id khối cần nhấn mạnh.
              Thời điểm: "at" là MỘT cụm 1-3 từ CHÉP NGUYÊN VĂN từ "narration" của cảnh, lúc khối xuất hiện;
              "until" (cụm từ lúc khối biến mất, để khối khác thay chỗ), "strikeAt" (gạch bỏ một ý sai), "revealAt" (lật đáp án).
              Tuỳ chọn: "motion" (pop, rise, slide, draw, type, count), "emphasis" (none, pulse, float, shake),
              "tone" (default, accent, good: ý đúng, bad: ý sai). Mọi biểu tượng dùng tên trong danh sách cho phép bên dưới.
              Cũng cung cấp "ambience" như ILLUSTRATED_EXPLAINER.
            - ILLUSTRATED_EXPLAINER (Trọng tâm): dùng để hình dung trực quan (cấu tạo, vai trò, chuỗi nhân quả, đám mây hạt).
              Cung cấp "headline" (tối đa 8 từ), "caption" (một câu ngắn), "ambience" (space: vũ trụ/vật lý; cell: sinh học;
              lab: hóa học/thí nghiệm; ocean: nước/Trái Đất; lilac, sky: nền sáng pastel xen kẽ với nền tối),
              "layout" (focus: một khái niệm trung tâm và các thành phần quanh nó; row: quy trình theo thứ tự có mũi tên;
              cluster: nhóm khái niệm ngang hàng; swarm: tập hợp nhiều hạt như tế bào, nguyên tử, đám mây electron;
              exchange: một bên trao một thứ cho bên kia, biểu tượng 1 là bên trao, 2 là bên nhận, 3 là thứ được trao
              (VD: chất khử nhường electron cho chất oxi hoá, lá cây nhận ánh sáng từ Mặt Trời);
              zoom: đi xuyên các thang đo từ lớn đến nhỏ, 2-4 biểu tượng theo thứ tự (VD: lá cây → tế bào → ADN))
              và "icons" (3-5 biểu tượng, mỗi cái có "name" BẮT BUỘC lấy từ danh sách [%s] và "label" tiếng Việt tối đa 3 từ).
              Với layout focus và swarm, biểu tượng đầu tiên là tâm điểm.
            - PROCESS_TIMELINE: Cung cấp "processTitle" và "stages" (3-4 giai đoạn, mỗi cái có stageNumber, title, description).
            - COMPARISON_SPLIT: Cung cấp "topicA" (title, badge, points), "topicB" (title, badge, points) và "conclusion".
            - MATH_FORMULA: Cung cấp "latex" (bắt buộc không rỗng) và "steps".
            - CHEMICAL_REACTION: Cung cấp "equation" (bắt buộc không rỗng), "reactants", "products", "condition", "observation".
            - DATA_CHART: Cung cấp "chartType" (bar/line), "xAxisLabel", "yAxisLabel", "dataPoints" (mảng có label và value là số).
            - OUTRO: Cung cấp "summaryPoints" (3 ý đúc kết cốt lõi) và "nextLessonSuggestion".
            """.formatted(request.topic(), request.subject(), request.gradeLevel(), approachInstruction, pacingInstruction, String.join(", ", StemIconCatalog.NAMES));

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

    /**
     * Proposes three clearly different ways to lead a video through one lesson.
     * The writer picks one, edits it, or writes their own before the draft is
     * generated, so videos on the same lesson do not all follow one pattern.
     */
    public AiActionResponse suggestApproaches(ApproachRequest request) {
        String prompt = """
            Bài học: [%s] | Môn: [%s] | Cấp độ: [%s]

            Nếu chủ đề không phù hợp hoặc không liên quan đến giáo dục STEM: đặt "isValidStemTopic": false,
            "fallbackMessage" là lời giải thích ngắn bằng tiếng Việt, "options" rỗng.

            Ngược lại, đặt "isValidStemTopic": true và đề xuất ĐÚNG 3 cách dẫn dắt một video ngắn (60-120 giây)
            dạy bài này, KHÁC NHAU THẬT SỰ về cách mở đầu và mạch đi (không chỉ đổi lời). Ít nhất một cách phải
            bất ngờ, sáng tạo (VD: trò chơi đố vui, một cuộc điều tra, phỏng vấn một hạt electron, nhật ký của nhà khoa học,
            phá một hiểu lầm, thử thách đoán số...). Cả ba phải phù hợp với học sinh %s và giữ kiến thức chính xác.

            Với mỗi cách:
            - "name": tên ngắn (tối đa 6 từ).
            - "hook": câu mở đầu video, đúng như sẽ đọc lên (một câu).
            - "flow": 4-7 bước của mạch, mỗi bước một câu ngắn, từ mở đầu đến chốt kiến thức.
            - "whyItFits": vì sao cách này giúp học sinh hiểu bài này (một câu).
            """.formatted(request.topic(), request.subject(), request.gradeLevel(), request.gradeLevel());

        AiActionResponse response = callAs(null, AiRequest.RequestType.ANALYSIS, prompt, AiSchemas.approaches(), false);
        JsonNode result = response.result();
        if (result != null && result.has("isValidStemTopic") && !result.get("isValidStemTopic").asBoolean()) {
            String fallback = result.hasNonNull("fallbackMessage")
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

            case metaphor -> """
                Đây là một cảnh trong video %s cho học sinh %s:

                %s

                Đề xuất ĐÚNG 3 ẩn dụ hình ảnh KHÁC NHAU để minh hoạ ý chính của cảnh này,
                theo phong cách video giải thích khoa học dạng hoạt hình phẳng: biến khái niệm trừu tượng
                thành một hình ảnh đời thường dễ nhớ (chuyền bóng, cán cân, nhà máy, đội quân, thành phố...).
                Ba phương án phải khác nhau về hình ảnh và nên dùng ít nhất 2 layout khác nhau.

                Với mỗi phương án:
                - "name": tên ngắn của ẩn dụ (tối đa 5 từ).
                - "idea": một câu mô tả hình ảnh sẽ thấy trên màn hình.
                - "whyItHelps": vì sao hình ảnh này giúp học sinh hiểu đúng.
                - "limitation": ẩn dụ này KHÔNG đúng ở điểm nào so với khoa học thật (bắt buộc, trung thực).
                - "layout": focus (một khái niệm trung tâm và các thành phần quanh nó), row (quy trình theo thứ tự),
                  cluster (nhóm ngang hàng), swarm (tập hợp rất nhiều hạt), exchange (biểu tượng 1 trao biểu tượng 3
                  cho biểu tượng 2, lặp lại nhiều lần) hoặc zoom (đi xuyên từ biểu tượng lớn nhất vào nhỏ nhất, 2-4 tầng).
                - "ambience": space, cell, lab, ocean, lilac hoặc sky, hợp với nội dung.
                - "headline": tiêu đề trên màn hình, tối đa 8 từ; "caption": một câu ngắn.
                - "icons": 2-5 biểu tượng, "name" BẮT BUỘC lấy từ danh sách [%s], "label" tiếng Việt tối đa 3 từ.
                - "narration": viết lại lời thoại của cảnh xoay quanh ẩn dụ này, giữ nguyên kiến thức,
                  độ dài gần bằng lời thoại gốc, nói rõ đây là phép so sánh.
                Kiến thức khoa học phải chính xác tuyệt đối; không thêm số liệu không có trong cảnh gốc.
                """.formatted(subject, grade, request.scriptText(), String.join(", ", StemIconCatalog.NAMES));

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
            case metaphor -> AiSchemas.metaphor();
        };

        return call(request.action(), prompt, schema);
    }

    public boolean isConfigured() {
        return geminiClient.isConfigured();
    }

    private AiActionResponse call(AiAction action, String prompt, Map<String, Object> schema) {
        boolean drafting = action == null || action == AiAction.resegment;
        return callAs(action, drafting ? AiRequest.RequestType.SEGMENTATION : AiRequest.RequestType.ANALYSIS,
            prompt, schema, drafting);
    }

    private AiActionResponse callAs(AiAction action, AiRequest.RequestType type, String prompt,
                                    Map<String, Object> schema, boolean listIcons) {
        long startedAt = System.currentTimeMillis();
        String instruction = SYSTEM_INSTRUCTION;
        if (listIcons) {
            instruction += "\nFor every icon name (icons[].name, icon.name, suggestedIcons[]), use only these exact names: "
                + String.join(", ", StemIconCatalog.NAMES) + ".";
        }
        var generation = geminiClient.generateJson(type, instruction, prompt, schema);
        JsonNode result = generation.result();
        replaceUnknownIcons(result);
        long elapsed = System.currentTimeMillis() - startedAt;

        if (elapsed > 15_000) {
            log.warn("AI action {} took {} ms, over the 15s target", action, elapsed);
        }

        return new AiActionResponse(action, result, generation.model(), elapsed, generation.requestId());
    }

    /**
     * Icon names the renderer cannot draw are swapped for the neutral sparkle
     * before the result reaches the writer. Regenerating a whole script over
     * one icon would spend the limited AI quota, and the producer reviews every
     * icon anyway.
     */
    private void replaceUnknownIcons(JsonNode result) {
        if (result == null) return;
        // "scenes" for generate and resegment, "options" for metaphor suggestions.
        java.util.List<JsonNode> holders = concat(result.path("scenes"), result.path("options"));
        for (JsonNode scene : java.util.List.copyOf(holders)) {
            // Blocks of a composed scene carry their own icon or icons.
            for (JsonNode block : scene.path("blocks")) {
                holders.add(block);
                if (block.path("icon") instanceof ObjectNode single && !isKnownIcon(single.path("name"))) {
                    log.warn("AI suggested unknown icon {}, using {}", single.path("name"), FALLBACK_ICON);
                    single.put("name", FALLBACK_ICON);
                }
            }
        }
        for (JsonNode scene : holders) {
            for (JsonNode icon : scene.path("icons")) {
                if (icon instanceof ObjectNode node && !isKnownIcon(node.path("name"))) {
                    log.warn("AI suggested unknown icon {}, using {}", node.path("name"), FALLBACK_ICON);
                    node.put("name", FALLBACK_ICON);
                }
            }
            if (scene.path("suggestedIcons") instanceof ArrayNode suggested) {
                for (int i = 0; i < suggested.size(); i++) {
                    if (!isKnownIcon(suggested.get(i))) {
                        log.warn("AI suggested unknown icon {}, using {}", suggested.get(i), FALLBACK_ICON);
                        suggested.set(i, FALLBACK_ICON);
                    }
                }
            }
        }
    }

    private static java.util.List<JsonNode> concat(JsonNode a, JsonNode b) {
        java.util.List<JsonNode> all = new java.util.ArrayList<>();
        a.forEach(all::add);
        b.forEach(all::add);
        return all;
    }

    private static boolean isKnownIcon(JsonNode name) {
        return name.isTextual() && StemIconCatalog.isKnown(name.asText());
    }
}
