package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.domain.ai.dto.AiDtos.AiAction;
import com.stemotion.domain.ai.dto.AiDtos.AiActionResponse;
import com.stemotion.domain.ai.dto.AiDtos.ScriptActionRequest;
import com.stemotion.integration.client.AiGateway;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The metaphor action proposes visual metaphors for one scene. Its schema must
 * let Gemini stage each option as a flat-explainer scene, including the two
 * storytelling layouts, and must always ask where the comparison breaks down.
 */
class AiMetaphorTest {

    @Test
    @DisplayName("each metaphor option is a ready flat-explainer staging with an honest limitation")
    @SuppressWarnings("unchecked")
    void metaphorSchemaDescribesAStagedScene() {
        Map<String, Object> schema = AiSchemas.metaphor();
        Map<String, Object> options = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("options");
        Map<String, Object> option = (Map<String, Object>) options.get("items");
        Map<String, Object> props = (Map<String, Object>) option.get("properties");

        assertThat((List<String>) option.get("required"))
            .contains("name", "idea", "limitation", "layout", "ambience", "headline", "icons", "narration");
        assertThat((List<String>) ((Map<String, Object>) props.get("layout")).get("enum"))
            .contains("exchange", "zoom", "focus");
    }

    @Test
    @DisplayName("the full-script schema offers the storytelling layouts too")
    @SuppressWarnings("unchecked")
    void generatedScriptOffersStorytellingLayouts() {
        Map<String, Object> scene = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
            AiSchemas.generatedScript().get("properties")).get("scenes")).get("items");
        Map<String, Object> layout = (Map<String, Object>) ((Map<String, Object>) scene.get("properties")).get("layout");

        assertThat((List<String>) layout.get("enum")).containsExactlyElementsOf(AiSchemas.FLAT_LAYOUTS);
    }

    @Test
    @DisplayName("the metaphor prompt carries the scene text and the approved icon names")
    void metaphorPromptCarriesSceneAndIcons() throws Exception {
        AiGateway gemini = mock(AiGateway.class);
        when(gemini.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(
            new ObjectMapper().readTree("{\"options\":[{\"name\":\"Chuyền bóng\",\"icons\":[{\"name\":\"unicorn\"}]}]}"),
            "test-gemini"));
        AiScriptService service = new AiScriptService(gemini, new IntegrationProperties());

        AiActionResponse response = service.runAction(new ScriptActionRequest(
            AiAction.metaphor,
            "Tiêu đề: Oxi hoá khử. Lời thoại: Chất khử nhường electron cho chất oxi hoá.",
            StemSubject.Chemistry,
            "Lớp 10"
        ));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> schema = ArgumentCaptor.forClass(Map.class);
        verify(gemini).generateJson(any(), anyString(), prompt.capture(), schema.capture());

        assertThat(response.action()).isEqualTo(AiAction.metaphor);
        assertThat(prompt.getValue())
            .contains("Chất khử nhường electron")
            .contains("ĐÚNG 3 ẩn dụ")
            .contains("test-tube");
        assertThat(schema.getValue()).isEqualTo(AiSchemas.metaphor());
        // An icon the renderer cannot draw is swapped for the sparkle, as in generated scripts.
        assertThat(response.result().at("/options/0/icons/0/name").asText()).isEqualTo("sparkle");
    }
}
