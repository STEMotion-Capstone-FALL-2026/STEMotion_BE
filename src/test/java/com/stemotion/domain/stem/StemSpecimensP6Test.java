package com.stemotion.domain.stem;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class StemSpecimensP6Test {
    static final ObjectMapper JSON=new ObjectMapper();static final JsonNode FIXTURES=load();
    static JsonNode load(){try{return JSON.readTree(StemSpecimensP6Test.class.getResourceAsStream("/stem-p6/contract-specimens.json"));}catch(Exception e){throw new IllegalStateException(e);}}
    static Stream<JsonNode> scenes(){return java.util.stream.StreamSupport.stream(FIXTURES.path("scenes").spliterator(),false);}
    static Stream<JsonNode> blocks(){return java.util.stream.StreamSupport.stream(FIXTURES.path("blocks").spliterator(),false);}
    final StemFingerprint fp=new StemFingerprint(JSON);final ScenePropsValidator validator=new ScenePropsValidator();
    void valid(JsonNode s){validator.validate(SceneType.valueOf(s.path("type").asText()),JSON.convertValue(s,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));}
    StemFingerprint.Result projection(JsonNode s){return fp.project(JSON.createArrayNode().add(s),JSON.createObjectNode(),JSON.createArrayNode());}
    @ParameterizedTest @MethodSource("scenes") void F09ValidDtoAndSemanticMutationForEachScene(JsonNode s){
        valid(s);var changed=s.deepCopy();String field=switch(s.path("type").asText()){case "MATH_FORMULA"->"latex";case "DATA_CHART"->"xAxisLabel";case "STEM_QUIZ"->"question";case "CHEMICAL_REACTION"->"equation";case "DIAGRAM_EXPLAINER"->"diagramTitle";case "PROCESS_TIMELINE"->"processTitle";case "GEOMETRY_SPACE"->"formulaLatex";case "ILLUSTRATED_EXPLAINER"->"headline";default->"title";};((ObjectNode)changed).put(field,"TEST_ONLY changed conclusion");assertThat(projection(s).semanticHash()).isNotEqualTo(projection(changed).semanticHash());
    }
    @ParameterizedTest @MethodSource("blocks") void F10ValidBlockAndExtraBlockWithConservativeMutation(JsonNode row){
        for(String key:List.of("scene","extraScene")){var s=row.path(key);valid(s);var changed=s.deepCopy();JsonNode values=changed.path(key.equals("scene")?"blocks":"extraBlocks");ObjectNode block=(ObjectNode)values.get(values.size()-1);
            switch(row.path("kind").asText()){
                case "formula"->block.put("latex","x-y");case "number"->block.put("value",5);case "choice"->block.put("answer",1);
                case "chart"->((ObjectNode)block.path("points").get(0)).put("value",5);
                case "list"->((ArrayNode)block.path("items")).set(0,JSON.getNodeFactory().textNode("TEST_ONLY different"));
                case "icon"->((ObjectNode)block.path("icon")).put("label","TEST_ONLY different");
                case "icons"->((ObjectNode)block.path("icons").get(0)).put("label","TEST_ONLY different");
                case "arrow","highlight"->block.put("to","TEST_ONLY-other-reference");default->block.put("text","TEST_ONLY different");
            }
            assertThat(projection(s).semanticHash()).isNotEqualTo(projection(changed).semanticHash());assertThat(projection(s).classification()).isEqualTo("UNKNOWN");}
    }
    @Test void F10DanglingReferenceIsRejected(){var s=FIXTURES.path("scenes").get(12).deepCopy();((ArrayNode)s.path("blocks")).add(JSON.createObjectNode().put("id","bad").put("kind","arrow").put("from","missing").put("to","a"));assertThatThrownBy(()->valid(s)).isInstanceOf(com.stemotion.core.exception.ApiException.class);}
    @Test void inventoryHasExactly13ScenesAnd12Kinds(){assertThat(FIXTURES.path("scenes")).hasSize(13);assertThat(FIXTURES.path("blocks")).hasSize(12);}
    @Test void F14AllFourVersionMismatchesRejectInheritanceWithoutChangingHistory(){
        var scene=FIXTURES.path("scenes").get(0);var hashes=projection(scene);var payload=JSON.createObjectNode().put("hashVersion",StemFingerprint.VERSION).put("canonicalVersion",StemFingerprint.CANONICAL).put("projectorVersion",StemFingerprint.PROJECTOR).put("classifierVersion",StemFingerprint.CLASSIFIER);
        var capture=new StemArtifactService.Capture("TEST_ONLY","v","w",null,null,payload,JSON.createArrayNode().add(scene),hashes);
        var service=new StemArtifactService(new StemRepository(null,JSON),null); // Reflection checks the existing pure matching predicate, never a database write.
        for(String field:List.of("hashVersion","canonicalVersion","projectorVersion","classifierVersion")){
            var old=payload.deepCopy();old.put(field,"TEST_ONLY-old-version");var row=new HashMap<String,Object>();row.put("version_id","v");row.put("semantic_hash",hashes.semanticHash());row.put("contract_hash",hashes.presentationContractHash());row.put("payload",old.toString());
            Boolean result=ReflectionTestUtils.invokeMethod(service,"matches",row,capture);assertThat(result).isFalse();assertThat(old.path(field).asText()).isEqualTo("TEST_ONLY-old-version");
        }
    }
}
