package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.ai.dto.AiDtos.ApproachRequest;
import com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest;
import com.stemotion.integration.client.AiGateway;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The way a video leads through a lesson is no longer fixed: the AI suggests
 * different approaches, the writer may pick one or write their own, and the
 * draft is built around it with composed scenes.
 */
class AiApproachTest {

    private final AiGateway client = mock(AiGateway.class);
    private final AiScriptService service = new AiScriptService(client, new IntegrationProperties());
    private final ObjectMapper mapper = new ObjectMapper();

    private String promptOf(Runnable call) throws Exception {
        call.run();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(any(), anyString(), prompt.capture(), anyMap());
        return prompt.getValue();
    }

    @Test
    @DisplayName("approach suggestions ask for three really different ways in")
    void suggestsThreeApproaches() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(
            mapper.readTree("{\"isValidStemTopic\":true,\"options\":[{\"name\":\"Gameshow\"}]}"), "test-gemini"));
        String prompt = promptOf(() -> service.suggestApproaches(
            new ApproachRequest("Định luật Ôm", StemSubject.Physics, "Lớp 11")));
        assertThat(prompt).contains("Định luật Ôm").contains("ĐÚNG 3 cách").contains("KHÁC NHAU THẬT SỰ");
    }

    @Test
    @DisplayName("an off-topic request for approaches is refused with the model's explanation")
    void refusesOffTopicApproaches() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(
            mapper.readTree("{\"isValidStemTopic\":false,\"fallbackMessage\":\"Không phải chủ đề STEM.\",\"options\":[]}"),
            "test-gemini"));
        assertThatThrownBy(() -> service.suggestApproaches(new ApproachRequest("bún chả", StemSubject.Physics, "10")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Không phải chủ đề STEM");
    }

    @Test
    @DisplayName("the writer's own approach is passed to the draft verbatim")
    void draftFollowsTheChosenApproach() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(
            mapper.readTree("{\"isValidStemTopic\":true,\"scenes\":[]}"), "test-gemini"));
        String prompt = promptOf(() -> service.generateScript(new GenerateScriptRequest(
            "Định luật Ôm", StemSubject.Physics, "11", 0, null, "Điều tra vì sao cầu chì bị cháy")));
        assertThat(prompt).contains("«Điều tra vì sao cầu chì bị cháy»").contains("COMPOSED");
    }

    @Test
    @DisplayName("without an approach the AI is asked to invent one, not to follow a fixed pattern")
    void draftInventsAnApproachWhenNoneIsGiven() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(
            mapper.readTree("{\"isValidStemTopic\":true,\"scenes\":[]}"), "test-gemini"));
        String prompt = promptOf(() -> service.generateScript(new GenerateScriptRequest(
            "Định luật Ôm", StemSubject.Physics, "11", 0)));
        assertThat(prompt).contains("tự sáng tạo MỘT cách dẫn dắt").doesNotContain("Cảnh 1 LUÔN dùng template TITLE_HERO");
    }

    @Test
    @DisplayName("unknown icons inside composed blocks fall back to the sparkle")
    void replacesUnknownIconsInBlocks() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(
            mapper.readTree("""
                {"isValidStemTopic":true,"scenes":[{"type":"COMPOSED","frame":"split","blocks":[
                  {"id":"a","kind":"icon","icon":{"name":"unicorn","label":"X"}},
                  {"id":"b","kind":"icons","icons":[{"name":"atom"},{"name":"dragon"}]}]}]}
                """), "test-gemini"));
        var blocks = service.generateScript(new GenerateScriptRequest("Atoms", StemSubject.Physics, "10", 0))
            .result().at("/scenes/0/blocks");
        assertThat(blocks.at("/0/icon/name").asText()).isEqualTo("sparkle");
        assertThat(blocks.at("/1/icons/0/name").asText()).isEqualTo("atom");
        assertThat(blocks.at("/1/icons/1/name").asText()).isEqualTo("sparkle");
    }

    @Test
    @DisplayName("the draft schema lets each scene be composed from blocks")
    @SuppressWarnings("unchecked")
    void draftSchemaOffersComposedScenes() {
        Map<String, Object> scene = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
            AiSchemas.generatedScript().get("properties")).get("scenes")).get("items");
        Map<String, Object> props = (Map<String, Object>) scene.get("properties");
        assertThat((List<String>) ((Map<String, Object>) props.get("type")).get("enum")).contains("COMPOSED");
        assertThat(props).containsKeys("frame", "blocks");
    }
}
