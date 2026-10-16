package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.ai.dto.AiDtos.GenerateScriptRequest;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import com.stemotion.integration.client.AiGateway;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The AI draft follows the registered proposal: the template set it lists
 * (title, formula, diagram, chart, algorithm, quiz, outro) comes first, and
 * each scene is placed in one of the five STEM lesson activities of Công văn
 * 3089, as the team committed to at Review 1.
 */
class ProposalAlignmentTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sceneProperties() {
        Map<String, Object> scene = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
            AiSchemas.generatedScript().get("properties")).get("scenes")).get("items");
        return (Map<String, Object>) scene.get("properties");
    }

    @Test
    @DisplayName("the draft can use every template the proposal registers")
    @SuppressWarnings("unchecked")
    void draftOffersTheRegisteredTemplates() {
        List<String> types = (List<String>) ((Map<String, Object>) sceneProperties().get("type")).get("enum");
        assertThat(types).contains("TITLE_HERO", "MATH_FORMULA", "DIAGRAM_EXPLAINER", "DATA_CHART",
            "ALGORITHM_WALKTHROUGH", "STEM_QUIZ", "OUTRO");
        assertThat(sceneProperties()).containsKeys("question", "options", "correctIndex",
            "diagramTitle", "labels", "codeSnippet", "stemPhase");
    }

    @Test
    @DisplayName("the prompt no longer bans quizzes and diagrams, and asks for the 3089 phases")
    void promptFavoursTheTemplateSetAndThePhases() throws Exception {
        AiGateway client = mock(AiGateway.class);
        when(client.generateJson(any(), anyString(), anyString(), anyMap())).thenReturn(new AiGateway.Generation(
            new ObjectMapper().readTree("{\"isValidStemTopic\":true,\"scenes\":[]}"), "test-gemini"));
        new AiScriptService(client, new IntegrationProperties())
            .generateScript(new GenerateScriptRequest("Định luật Ôm", StemSubject.Physics, "11", 0));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(any(), anyString(), prompt.capture(), anyMap());
        assertThat(prompt.getValue())
            .doesNotContain("KHÔNG dùng STEM_QUIZ")
            .contains("BẢN NHÁP GỢI Ý")
            .contains("STEM_QUIZ: Cung cấp")
            .contains("Công văn 3089")
            .contains("PROBLEM").contains("SHARE");
    }

    @Test
    @DisplayName("a scene may carry one of the five STEM phases and nothing else")
    void stemPhaseIsValidated() {
        ScenePropsValidator validator = new ScenePropsValidator();
        Map<String, Object> props = new HashMap<>(Map.of("latex", "I = U/R", "steps", List.of(), "stemPhase", "RESEARCH"));
        assertThatCode(() -> validator.validate(SceneType.MATH_FORMULA, props)).doesNotThrowAnyException();

        props.put("stemPhase", "HOMEWORK");
        assertThatThrownBy(() -> validator.validate(SceneType.MATH_FORMULA, props))
            .isInstanceOf(ApiException.class).hasMessageContaining("stemPhase must be one of");
    }
}
