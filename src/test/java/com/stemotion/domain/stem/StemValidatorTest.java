package com.stemotion.domain.stem;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.exception.ApiException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class StemValidatorTest {
 ObjectMapper json=new ObjectMapper();StemValidator validator=new StemValidator(json);
 @Test void draftNullPrimaryIsAllowedButIncomplete(){var f=validator.check(StemDtos.empty(),List.of());assertThat(f).anyMatch(x->x.ruleId().equals("TECH-CONTEXT"));}
 @Test void channelCannotBePrimary(){var b=StemDtos.empty();var bad=new StemDtos.Brief(null,null,null,List.of(),List.of(),null,new StemDtos.UsageContext(new StemDtos.ContextEntry("DEPARTMENT_LIBRARY",null,null),List.of()),List.of(),List.of(),List.of(),List.of(),null,null);assertThatThrownBy(()->validator.check(bad,List.of())).isInstanceOf(ApiException.class);}
 @Test void unknownClaimsAreRejected(){var n=json.createObjectNode().put("schemaVersion","stem.v1").put("expectedScriptRevision",0).put("idempotencyKey","test-key-01").put("verified",true);assertThatThrownBy(()->validator.parse(n,StemDtos.Create.class)).isInstanceOf(ApiException.class);}
 @Test void strictIsOnlyPureNegativeEvaluator(){assertThatThrownBy(()->validator.transition("STRICT",validator.check(StemDtos.empty(),List.of()),false)).isInstanceOf(ApiException.class);validator.transition("OFF",validator.check(StemDtos.empty(),List.of()),false);validator.transition("ADVISORY",validator.check(StemDtos.empty(),List.of()),false);}
 @Test void aiWarningAloneIsNotAHardGate(){validator.transition("STRICT",List.of(new StemDtos.Finding("AI","narration","WARN","Synthetic advisory")),true);}
 @Test void limitsAndMissingRevisionHaveFieldErrors(){assertThatThrownBy(()->validator.rev(null,"baseRevision")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getDetails()).containsEntry("field","baseRevision"));assertThatThrownBy(()->validator.limits(json.getNodeFactory().textNode("x".repeat(4001)),0)).isInstanceOf(ApiException.class);}
 @Test void evidenceNeedsATargetAndMatchingTask(){assertThatThrownBy(()->validator.check(StemDtos.empty(),List.of(new StemDtos.Evidence(null,null,null,null,"S","TEST_ONLY")))).isInstanceOf(ApiException.class);assertThatThrownBy(()->validator.check(StemDtos.empty(),List.of(new StemDtos.Evidence(null,null,"wrong",null,"S","TEST_ONLY")))).isInstanceOf(ApiException.class);}
 @Test void legacyDecisionJsonStillDeserializesAndClaimsDoNot()throws Exception{var d=json.readValue("{\"note\":\"TEST_ONLY\"}",com.stemotion.domain.script.dto.ScriptDtos.ReviewDecisionRequest.class);assertThat(d.note()).isEqualTo("TEST_ONLY");assertThat(d.stemAssessment()).isNull();assertThatThrownBy(()->json.readValue("{\"verified\":true}",com.stemotion.domain.script.dto.ScriptDtos.ReviewDecisionRequest.class)).isInstanceOf(Exception.class);}
 @Test void unapprovedHypothesisContextsAreNotAddedFromMaster(){for(String key:List.of("PRE_CLASS","HOME_SELF_STUDY")){var b=StemDtos.empty();var value=new StemDtos.Brief(null,null,null,List.of(),List.of(),null,new StemDtos.UsageContext(new StemDtos.ContextEntry(key,null,null),List.of()),List.of(),List.of(),List.of(),List.of(),null,null);assertThatThrownBy(()->validator.check(value,List.of())).isInstanceOf(ApiException.class);}}
}
