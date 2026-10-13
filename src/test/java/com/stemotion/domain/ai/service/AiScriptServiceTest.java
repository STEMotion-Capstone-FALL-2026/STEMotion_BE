package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.domain.ai.dto.AiDtos.*;
import com.stemotion.integration.client.AiGateway;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiScriptServiceTest {
    private final AiGateway client = mock(AiGateway.class);
    private final AiScriptService service = new AiScriptService(client, new IntegrationProperties());
    private final ObjectMapper mapper = new ObjectMapper();
    private final GenerateScriptRequest request =
        new GenerateScriptRequest("Atoms", StemSubject.Physics, "10", 90);

    @Test
    void suppliesCatalogInInstructionAndPreservesSceneContract() throws Exception {
        var output = mapper.readTree("""
            {"isValidStemTopic":true,"fallbackMessage":null,"scenes":[
              {"type":"ILLUSTRATED_EXPLAINER","title":"Atoms","narration":"Atom",
               "durationInFrames":90,"icons":[{"name":"atom","label":"Atom"}]}]}
            """);
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(output, "test-gemini"));
        assertThat(service.generateScript(request).result()).isEqualTo(output);
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
    void replacesUnknownGeneratedIconBeforeReturningToFrontend() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(mapper.readTree(
            "{\"isValidStemTopic\":true,\"scenes\":[{\"icons\":[{\"name\":\"imaginary-icon\",\"label\":\"X\"},{\"name\":\"atom\"}]}]}"), "test-gemini"));
        var icons = service.generateScript(request).result().at("/scenes/0/icons");
        assertThat(icons.get(0).path("name").asText()).isEqualTo("sparkle");
        assertThat(icons.get(0).path("label").asText()).isEqualTo("X");
        assertThat(icons.get(1).path("name").asText()).isEqualTo("atom");
    }

    @Test
    void replacesUnknownResegmentedIconSuggestionsToo() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(mapper.readTree(
            "{\"scenes\":[{\"suggestedIcons\":[\"imaginary-icon\",\"leaf\"]}]}"), "test-gemini"));
        var suggested = service.runAction(new ScriptActionRequest(
            AiAction.resegment, "Atoms", StemSubject.Physics, "10")).result().at("/scenes/0/suggestedIcons");
        assertThat(suggested.get(0).asText()).isEqualTo("sparkle");
        assertThat(suggested.get(1).asText()).isEqualTo("leaf");
    }

    @Test
    void preservesInvalidTopicGuardrail() throws Exception {
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(mapper.readTree(
            "{\"isValidStemTopic\":false,\"fallbackMessage\":\"Invalid topic\",\"scenes\":[]}"), "test-gemini"));
        assertThatThrownBy(() -> service.generateScript(request))
            .isInstanceOf(ApiException.class).hasMessage("Invalid topic");
    }
}
