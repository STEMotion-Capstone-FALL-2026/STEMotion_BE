package com.stemotion.domain.ai.service;
import com.fasterxml.jackson.databind.*;
import com.stemotion.domain.stem.StemValidator;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class StemGroundedOutputP6Test {
    final ObjectMapper json=new ObjectMapper();final StemGroundedAiService service=new StemGroundedAiService(null,null,json,new ScenePropsValidator(),new StemValidator(json));
    @ParameterizedTest @ValueSource(strings={"verificationStatus","stemAssessment","sourceId","outcomeId","sources","outcomes","grounding","stemContext"})
    void rejectsModelAuthorityFields(String key){var output=json.createObjectNode().put(key,"VERIFIED");assertThatThrownBy(()->service.validateOutput(output,json.createObjectNode(),"terms")).isInstanceOf(com.stemotion.core.exception.ApiException.class).extracting("code").isEqualTo("AI_INVALID_OUTPUT");}
    @Test void oversizedModelOutputIsProviderErrorNotUserValidationError(){var output=json.createObjectNode().put("notes","x".repeat(4001));assertThatThrownBy(()->service.validateOutput(output,json.createObjectNode(),"terms")).extracting("code").isEqualTo("AI_INVALID_OUTPUT");}
    @Test void anAnalysisScoreDoesNotBypassItsSchema(){assertThatThrownBy(()->service.validateOutput(json.createObjectNode().put("readabilityScore",100),json.createObjectNode(),"grade")).isInstanceOf(com.stemotion.core.exception.ApiException.class);}
    @Test void emptyTerminologyFindingsRemainAdviceOnly(){var output=json.createObjectNode();output.putArray("issues");assertThatCode(()->service.validateOutput(output,json.createObjectNode(),"terms")).doesNotThrowAnyException();}
}
