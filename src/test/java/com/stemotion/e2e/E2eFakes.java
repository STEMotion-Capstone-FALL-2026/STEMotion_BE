package com.stemotion.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stemotion.integration.client.GeminiClient;
import com.stemotion.integration.client.RenderServiceClient;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stand-ins for the paid outside services, used only by {@link E2eServer}. Everything behind
 * them (AI request log, render jobs, callbacks, clips, QA) is the real application code.
 */
final class E2eFakes {

    private E2eFakes() {}

    /** A project whose title contains this marker gets a render that fails (DS-03b). */
    static final String FAIL_RENDER_MARKER = "[render-fail]";

    /** Answers every AI request locally: a fixed draft for script generation, schema-shaped JSON otherwise. */
    static class FakeGemini extends GeminiClient {
        private final ObjectMapper mapper;

        FakeGemini(IntegrationProperties properties, ObjectMapper mapper) {
            super(properties, mapper);
            this.mapper = mapper;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public JsonNode generateJson(String systemInstruction, String prompt, Map<String, Object> responseSchema) {
            return generateWithUsage(systemInstruction, prompt, responseSchema).result();
        }

        @Override
        public UsageResult generateWithUsage(String systemInstruction, String prompt, Map<String, Object> responseSchema) {
            Map<?, ?> properties = responseSchema.get("properties") instanceof Map<?, ?> m ? m : Map.of();
            JsonNode result = properties.containsKey("isValidStemTopic") && properties.containsKey("scenes")
                ? draft(prompt)
                : sample(responseSchema, "result");
            return new UsageResult(result, 120, 480, 600);
        }

        /** Two scenes of 10 s whose props pass ScenePropsValidator. */
        private JsonNode draft(String prompt) {
            // The prompt carries "Topic người dùng: [<topic>]"; the draft is titled after it, as Gemini does.
            var topic = java.util.regex.Pattern.compile("Topic người dùng: \\[(.*?)]").matcher(prompt);
            ObjectNode draft = mapper.createObjectNode()
                .put("isValidStemTopic", true)
                .putNull("fallbackMessage")
                .put("title", topic.find() ? topic.group(1) : "Định luật Ôm")
                .put("gradeLevel", "Cơ bản")
                .put("totalDurationSeconds", 20)
                .put("approachName", "Thí nghiệm mạch điện");
            draft.putArray("flow").add("Đặt câu hỏi").add("Rút ra công thức");
            draft.putObject("narrativeArc").put("summary", "Từ thí nghiệm đến công thức.").put("approach", "Thí nghiệm");
            ArrayNode scenes = draft.putArray("scenes");
            scenes.add(formulaScene("Cường độ dòng điện", "Cường độ dòng điện tỉ lệ thuận với hiệu điện thế giữa hai đầu dây.", "I = \\frac{U}{R}"));
            scenes.add(formulaScene("Điện trở", "Điện trở cho biết dây dẫn cản trở dòng điện nhiều hay ít.", "R = \\frac{U}{I}"));
            return draft;
        }

        private ObjectNode formulaScene(String title, String narration, String latex) {
            ObjectNode scene = mapper.createObjectNode()
                .put("type", "MATH_FORMULA")
                .put("title", title)
                .put("narration", narration)
                .put("durationInFrames", 300)
                .put("latex", latex);
            scene.putArray("steps").addObject()
                .put("label", "Công thức")
                .put("latexSnippet", latex)
                .put("explanation", narration);
            return scene;
        }

        /** Minimal JSON that satisfies a Gemini response schema (OBJECT/ARRAY/STRING/NUMBER/BOOLEAN). */
        private JsonNode sample(Map<?, ?> schema, String name) {
            String type = String.valueOf(schema.get("type")).toUpperCase();
            switch (type) {
                case "OBJECT" -> {
                    ObjectNode node = mapper.createObjectNode();
                    Object rawProps = schema.get("properties");
                    Map<?, ?> props = rawProps instanceof Map<?, ?> m ? m : Map.of();
                    props.forEach((key, value) -> node.set(String.valueOf(key), sample((Map<?, ?>) value, String.valueOf(key))));
                    return node;
                }
                case "ARRAY" -> {
                    ArrayNode node = mapper.createArrayNode();
                    Map<?, ?> items = (Map<?, ?>) schema.get("items");
                    for (int i = 0; i < 2 && items != null; i++) node.add(sample(items, name));
                    return node;
                }
                case "NUMBER", "INTEGER" -> {
                    return mapper.getNodeFactory().numberNode(1);
                }
                case "BOOLEAN" -> {
                    return mapper.getNodeFactory().booleanNode(true);
                }
                default -> {
                    List<?> options = (List<?>) schema.get("enum");
                    return mapper.getNodeFactory().textNode(options != null && !options.isEmpty()
                        ? String.valueOf(options.get(0)) : "E2E " + name);
                }
            }
        }
    }

    /**
     * Render worker stand-in: accepts a job and reports it back through the real callback
     * endpoint (PROCESSING, then COMPLETED or FAILED), as the Node worker does.
     */
    @Slf4j
    static class FakeRenderWorker extends RenderServiceClient {
        private final IntegrationProperties properties;
        private final ObjectMapper mapper;
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

        FakeRenderWorker(IntegrationProperties properties, ObjectMapper mapper) {
            super(properties);
            this.properties = properties;
            this.mapper = mapper;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public EnqueueResult enqueue(String jobId, String composition, Map<String, Object> script,
                                     int width, int height, int fps, String ttsVoice) {
            boolean fail = String.valueOf(script.get("title")).contains(FAIL_RENDER_MARKER);
            // Callbacks come after the enqueueing transaction has committed, as from the real worker.
            timer.schedule(() -> callback(jobId, "PROCESSING", 50, null, null), 1500, TimeUnit.MILLISECONDS);
            timer.schedule(() -> {
                if (fail) callback(jobId, "FAILED", 50, null, "E2E: render worker unavailable");
                else callback(jobId, "COMPLETED", 100, "https://e2e.example.org/videos/" + jobId + ".mp4", null);
            }, 3000, TimeUnit.MILLISECONDS);
            return new EnqueueResult(jobId, 5);
        }

        @Override
        public void cancel(String externalJobId) {
            // nothing queued outside this process
        }

        /** Half a second of silence as WAV. */
        @Override
        public byte[] ttsPreview(String text) {
            int rate = 8000, samples = rate / 2;
            ByteBuffer wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
            wav.put("RIFF".getBytes()).putInt(36 + samples * 2).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(samples * 2);
            return wav.array();
        }

        /** Durations from the narration length: about 4 words per second, at least 3 s. */
        @Override
        public JsonNode syncDurations(Map<String, Object> script, int fps) {
            ObjectNode response = mapper.createObjectNode();
            ArrayNode scenes = response.putObject("script").putArray("scenes");
            for (Object raw : (List<?>) script.getOrDefault("scenes", List.of())) {
                Map<?, ?> scene = (Map<?, ?>) raw;
                String narration = String.valueOf(scene.get("narration"));
                int words = narration.isBlank() ? 0 : narration.trim().split("\\s+").length;
                double seconds = Math.max(3, words / 4.0);
                scenes.addObject()
                    .put("id", String.valueOf(scene.get("id")))
                    .put("durationInFrames", (int) Math.round(seconds * fps));
            }
            return response;
        }

        private void callback(String jobId, String status, int progress, String videoUrl, String error) {
            try {
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("jobId", jobId);
                body.put("status", status);
                body.put("progress", progress);
                body.put("stage", status.toLowerCase());
                if (videoUrl != null) body.put("videoUrl", videoUrl);
                if (error != null) body.put("errorMessage", error);
                RestClient.create().post()
                    .uri(properties.getRender().getCallbackUrl())
                    .header("X-Render-Api-Key", properties.getRender().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
            } catch (Exception ex) {
                log.warn("E2E render callback {} for {} failed: {}", status, jobId, ex.getMessage());
            }
        }
    }
}
