package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.ai.controller.AiController;
import com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest;
import com.stemotion.integration.client.AiGateway;
import com.stemotion.domain.ai.dto.AiDtos.*;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiScriptServiceTest {
    private final AiGateway client = mock(AiGateway.class);
    private final AiScriptService service = new AiScriptService(client, new IntegrationProperties());
    private final ObjectMapper mapper = new ObjectMapper();

    private GenerateScriptRequest request(Integer duration, String brief) {
        return new GenerateScriptRequest("Định luật Ôm", StemSubject.Physics, "10", duration, brief);
    }

    private String generatedPrompt(Integer duration, String brief) throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(generation(
            mapper.readTree("{\"isValidStemTopic\":true,\"scenes\":[]}")));
        service.generateScript(request(duration, brief));
        var prompt = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(any(), anyString(), prompt.capture(), anyMap());
        return prompt.getValue();
    }

    @Test
    void includesWriterBriefBetweenGuardrailAndGenerationWithoutChangingConstraints() throws Exception {
        String brief = "Dạy định luật Ôm như một gameshow, phỏng vấn một electron";
        String prompt = generatedPrompt(90, brief);
        assertThat(prompt).contains("Ý tưởng dẫn dắt của Writer: [" + brief + "]",
            "BẮT BUỘC dựng toàn bộ mạch", "GỢI Ý THAM KHẢO",
            "Cảnh 1 LUÔN dùng template TITLE_HERO", "Cảnh cuối LUÔN dùng template OUTRO",
            "KHÔNG dùng STEM_QUIZ, DIAGRAM_EXPLAINER hay GEOMETRY_SPACE",
            "Nội dung khoa học, công thức phải CHÍNH XÁC TUYỆT ĐỐI");
        assertThat(prompt.indexOf("BƯỚC 1:")).isLessThan(prompt.indexOf("BƯỚC 1.5:"));
        assertThat(prompt.indexOf("BƯỚC 1.5:")).isLessThan(prompt.indexOf("BƯỚC 2:"));
        assertThat(mapper.valueToTree(AiSchemas.generatedScript())
            .at("/properties/scenes/items/properties/type/enum").toString())
            .doesNotContain("STEM_QUIZ", "DIAGRAM_EXPLAINER", "GEOMETRY_SPACE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  \n\t"})
    void missingOrBlankBriefLetsAiChoose(String brief) throws Exception {
        assertThat(generatedPrompt(null, brief)).contains("Ý tưởng dẫn dắt của Writer: [không có — bạn tự quyết]");
    }

    @Test
    void rejectsOverlongBriefAtHttpValidationBeforeCallingGemini() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AiController(service, mock(AiRequestLogService.class))).build();
        mvc.perform(post("/api/v1/ai/script/generate").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request(90, "a".repeat(501)))))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(client);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request(90, "a".repeat(501)));
            assertThat(violations).singleElement().satisfies(v -> {
                assertThat(v.getPropertyPath().toString()).isEqualTo("narrativeBrief");
                assertThat(v.getMessage()).contains("500");
            });
            assertThat(factory.getValidator().validate(request(90, "a".repeat(500)))).isEmpty();
        }
    }

    @Test
    void preservesArcAndSegmentsInResultAndKeepsArcOptionalInSchema() throws Exception {
        var output = mapper.readTree("""
            {"isValidStemTopic":true,"scenes":[],"narrativeArc":{
              "summary":"Gameshow khám phá định luật Ôm.","approach":"gameshow",
              "segments":[{"order":1,"title":"Electron lên sân khấu","purpose":"Gây tò mò"}]}}
            """);
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(generation(output));
        var result = service.generateScript(request(90, "gameshow")).result();
        assertThat(result.get("narrativeArc")).isEqualTo(output.get("narrativeArc"));
        assertThat(result.at("/narrativeArc/segments/0/order").asInt()).isEqualTo(1);
        var schema = ArgumentCaptor.forClass(java.util.Map.class);
        verify(client).generateJson(any(), anyString(), anyString(), schema.capture());
        var jsonSchema = mapper.valueToTree(schema.getValue());
        assertThat(jsonSchema.get("required").toString()).doesNotContain("narrativeArc");
        assertThat(jsonSchema.at("/properties/narrativeArc/required").toString())
            .contains("summary", "approach").doesNotContain("segments");
        assertThat(jsonSchema.at("/properties/narrativeArc/properties/segments/items/required").toString())
            .contains("order", "title", "purpose");
    }

    @Test
    void invalidTopicStillThrowsBadRequestWithoutArcEvenWhenWriterProvidesBrief() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(generation(mapper.readTree(
            "{\"isValidStemTopic\":false,\"fallbackMessage\":\"Chủ đề không hợp lệ\",\"scenes\":[]}")));
        assertThatThrownBy(() -> service.generateScript(request(null, "gameshow")))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ex.getCode()).isEqualTo("BAD_REQUEST");
                assertThat(ex.getMessage()).isEqualTo("Chủ đề không hợp lệ");
            });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 60, 90, 120})
    void preservesAllDynamicPacingBranches(int duration) throws Exception {
        String expected = switch (duration) {
            case 0 -> "DYNAMIC SCENE COUNT TỪ 4 ĐẾN 9 CẢNH";
            case 60 -> "Tạo 4-5 cảnh";
            case 90 -> "Tạo 6-7 cảnh";
            default -> "Tạo 8-10 cảnh";
        };
        String prompt = generatedPrompt(duration, "gameshow");
        assertThat(prompt).contains(expected);
        if (duration > 0) assertThat(prompt).contains("xấp xỉ " + duration + " giây");
    }

    @Test
    void olderJsonRequestsWithoutBriefRemainCompatible() throws Exception {
        var oldRequest = mapper.readValue(
            "{\"topic\":\"Atoms\",\"subject\":\"Physics\",\"gradeLevel\":\"10\",\"targetDurationSec\":90}",
            GenerateScriptRequest.class);
        assertThat(oldRequest.narrativeBrief()).isNull();
        assertThat(oldRequest.targetDurationSec()).isEqualTo(90);
    }
    private AiGateway.Generation generation(com.fasterxml.jackson.databind.JsonNode result) {
        return new AiGateway.Generation(result, "test-gemini", "test-request", 12, 8, 20);
    }
    @Test
    void suppliesCatalogInInstructionAndPreservesSceneContract() throws Exception {
        var output = mapper.readTree("""
            {"isValidStemTopic":true,"fallbackMessage":null,"scenes":[
              {"type":"ILLUSTRATED_EXPLAINER","title":"Atoms","narration":"Atom",
               "durationInFrames":90,"icons":[{"name":"atom","label":"Atom"}]}]}
            """);
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(output, "test-gemini"));
        assertThat(service.generateScript(request(90, null)).result()).isEqualTo(output);
        var instruction = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(any(), instruction.capture(), anyString(), anyMap());
        for (String name : StemIconCatalog.NAMES) {
            assertThat(instruction.getValue()).contains(name);
        }
        // Regression: the real Gemini endpoint rejects this schema with the large icon enum.
        var schema = mapper.valueToTree(AiSchemas.generatedScript());
        assertThat(schema.at("/properties/scenes/items/properties/icons/items/properties/name/enum").isMissingNode()).isTrue();
        assertThat(schema.at("/properties/fallbackMessage/nullable").asBoolean()).isTrue();
    }

    @Test
    void rejectsUnknownGeneratedIconBeforeReturningToFrontend() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(mapper.readTree(
            "{\"isValidStemTopic\":true,\"scenes\":[{\"icons\":[{\"name\":\"imaginary-icon\"}]}]}"), "test-gemini"));
        assertThatThrownBy(() -> service.generateScript(request(90, null))).isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("AI_INVALID_RESPONSE"));
    }

    @Test
    void validatesResegmentedIconSuggestionsToo() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(mapper.readTree(
            "{\"scenes\":[{\"suggestedIcons\":[\"imaginary-icon\"]}]}"), "test-gemini"));
        assertThatThrownBy(() -> service.runAction(new ScriptActionRequest(
            AiAction.resegment, "Atoms", StemSubject.Physics, "10")))
            .isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("AI_INVALID_RESPONSE"));
    }

    @Test
    void preservesInvalidTopicGuardrail() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(mapper.readTree(
            "{\"isValidStemTopic\":false,\"fallbackMessage\":\"Invalid topic\",\"scenes\":[]}"), "test-gemini"));
        assertThatThrownBy(() -> service.generateScript(request(90, null)))
            .isInstanceOf(ApiException.class).hasMessage("Invalid topic");
    }
}
