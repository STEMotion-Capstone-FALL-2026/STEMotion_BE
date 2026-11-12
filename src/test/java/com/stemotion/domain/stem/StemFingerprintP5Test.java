package com.stemotion.domain.stem;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class StemFingerprintP5Test {
    private final ObjectMapper json=new ObjectMapper();
    private final StemFingerprint fp=new StemFingerprint(json);
    @TestFactory Stream<DynamicTest> approvedCanonicalGoldenVectors() throws Exception {
        JsonNode vectors=json.readTree(Files.readString(Path.of("docs/stem/phases/phase-05/CANONICAL_VECTORS.json"))).path("vectors");
        List<DynamicTest> tests=new ArrayList<>();
        for(JsonNode v:vectors)tests.add(DynamicTest.dynamicTest(v.path("id").asText(),()->{
            if(v.has("expectedStatus"))assertThatThrownBy(()->fp.canonical(fp.normalizedSets(fp.parse(v.path("inputLiteral").asText())))).isInstanceOf(com.stemotion.core.exception.ApiException.class);
            else {
                JsonNode input=fp.normalizedSets(fp.parse(v.path("inputLiteral").asText()));
                assertThat(fp.canonical(input)).isEqualTo(v.path("canonicalUtf8Literal").asText());
                if(StemFingerprint.VERSION.equals(v.path("hashVersion").asText()))assertThat(fp.hash(v.path("projection").asText(),input)).isEqualTo(v.path("expectedSha256").asText());
                else assertThat(fp.hash(v.path("projection").asText(),input)).isNotEqualTo(v.path("expectedSha256").asText());
            }
        }));return tests.stream();
    }
    private ArrayNode scene(String type){return json.createArrayNode().add(json.createObjectNode().put("id","TEST_ONLY_scene").put("type",type).put("title","TEST_ONLY").put("narration","không đổi đơn vị").put("durationInFrames",180));}
    private StemFingerprint.Result project(JsonNode s){return fp.project(s,json.createObjectNode(),json.createArrayNode());}
    @ParameterizedTest @ValueSource(strings={
        "TITLE_HERO:subtitle","TITLE_HERO:subject","TITLE_HERO:gradeLevel","TITLE_HERO:badgeText",
        "MATH_FORMULA:latex","MATH_FORMULA:steps","DIAGRAM_EXPLAINER:diagramTitle","DIAGRAM_EXPLAINER:labels","DIAGRAM_EXPLAINER:svgType",
        "DATA_CHART:chartType","DATA_CHART:xAxisLabel","DATA_CHART:yAxisLabel","DATA_CHART:dataPoints",
        "ALGORITHM_WALKTHROUGH:language","ALGORITHM_WALKTHROUGH:codeSnippet","ALGORITHM_WALKTHROUGH:steps",
        "STEM_QUIZ:question","STEM_QUIZ:options","STEM_QUIZ:correctIndex","STEM_QUIZ:hint","STEM_QUIZ:explanation",
        "OUTRO:summaryPoints","OUTRO:nextLessonSuggestion","OUTRO:instructorName",
        "CHEMICAL_REACTION:equation","CHEMICAL_REACTION:reactants","CHEMICAL_REACTION:products","CHEMICAL_REACTION:condition","CHEMICAL_REACTION:observation",
        "COMPARISON_SPLIT:topicA","COMPARISON_SPLIT:topicB","COMPARISON_SPLIT:conclusion",
        "PROCESS_TIMELINE:processTitle","PROCESS_TIMELINE:stages",
        "GEOMETRY_SPACE:shapeType","GEOMETRY_SPACE:theoremName","GEOMETRY_SPACE:formulaLatex","GEOMETRY_SPACE:dimensions","GEOMETRY_SPACE:explanation",
        "ILLUSTRATED_EXPLAINER:headline","ILLUSTRATED_EXPLAINER:caption","ILLUSTRATED_EXPLAINER:icons","ILLUSTRATED_EXPLAINER:layout","COMPOSED:frame"})
    void semanticFieldMutationCannotKeepApproval(String entry){
        String[] pair=entry.split(":");ArrayNode a=scene(pair[0]),b=a.deepCopy();
        ((ObjectNode)a.get(0)).put(pair[1],"TEST_ONLY_original");((ObjectNode)b.get(0)).put(pair[1],"TEST_ONLY_changed");
        assertThat(project(a).semanticHash()).isNotEqualTo(project(b).semanticHash());
    }
    @ParameterizedTest @ValueSource(strings={"title","text","list","formula","icon","icons","number","chart","choice","arrow","highlight","guide"})
    void everyBlockAndExtraBlockRetainsSemanticContent(String kind){
        for(String field:List.of("blocks","extraBlocks")){
            ArrayNode a=scene(field.equals("blocks")?"COMPOSED":"TITLE_HERO"),b=a.deepCopy();
            ((ObjectNode)a.get(0)).putArray(field).add(json.createObjectNode().put("id","b1").put("kind",kind).put("text","không dẫn điện"));
            ((ObjectNode)b.get(0)).putArray(field).add(json.createObjectNode().put("id","b1").put("kind",kind).put("text","dẫn điện"));
            assertThat(project(a).semanticHash()).isNotEqualTo(project(b).semanticHash());
        }
    }
    @Test void safeLongerAlignmentKeepsSemanticAndContractButChangesRenderInput(){
        ArrayNode a=scene("TITLE_HERO"),b=a.deepCopy();((ObjectNode)b.get(0)).put("durationInFrames",210);
        var x=project(a);var y=project(b);assertThat(x.semanticHash()).isEqualTo(y.semanticHash());assertThat(x.presentationContractHash()).isEqualTo(y.presentationContractHash());
        assertThat(fp.compatible(a,b,x,y)).isTrue();assertThat(fp.hash("renderInput",a)).isNotEqualTo(fp.hash("renderInput",b));
        assertThat(fp.compatible(b,a,y,x)).isFalse();
    }
    @Test void captionTokensAndCutoffCannotBeHiddenByTimingProjection(){
        ArrayNode a=scene("TITLE_HERO"),b=a.deepCopy();
        ((ObjectNode)a.get(0)).putArray("captionWords").add(json.createObjectNode().put("text","không").put("start",0).put("end",1));
        ((ObjectNode)b.get(0)).putArray("captionWords").add(json.createObjectNode().put("text","có").put("start",0).put("end",1));
        assertThat(project(a).semanticHash()).isNotEqualTo(project(b).semanticHash());
        b=a.deepCopy();((ObjectNode)b.get(0)).put("durationInFrames",15);assertThat(fp.compatible(a,b,project(a),project(b))).isFalse();
    }
    @Test void unknownRawSubtreeIsRetainedAndCannotInherit(){
        ArrayNode a=scene("TITLE_HERO"),b=a.deepCopy();((ObjectNode)a.get(0)).putObject("futureRule").put("voltage",5);((ObjectNode)b.get(0)).putObject("futureRule").put("voltage",50);
        assertThat(project(a).classification()).isEqualTo("UNKNOWN");assertThat(project(a).semanticHash()).isNotEqualTo(project(b).semanticHash());assertThat(fp.compatible(a,a,project(a),project(a))).isFalse();
    }
    @Test void themeChangesContractAndAudioWithoutContentProofIsUnknown(){
        ArrayNode a=scene("TITLE_HERO"),b=a.deepCopy();((ObjectNode)b.get(0)).put("cardTheme","contrast");
        assertThat(project(a).semanticHash()).isEqualTo(project(b).semanticHash());assertThat(project(a).presentationContractHash()).isNotEqualTo(project(b).presentationContractHash());
        ((ObjectNode)a.get(0)).put("narrationAudioUrl","https://example.invalid/audio?signature=one");((ObjectNode)b.get(0)).put("narrationAudioUrl","https://example.invalid/audio?signature=two");assertThat(project(a).classification()).isEqualTo("UNKNOWN");assertThat(fp.compatible(a,b,project(a),project(b))).isFalse();
    }
    @Test void sceneReorderAndCueRelationshipChangesAreSemantic(){
        ArrayNode a=scene("TITLE_HERO");a.add(json.createObjectNode().put("id","second").put("type","OUTRO").put("title","conclusion").put("durationInFrames",180));
        ArrayNode b=json.createArrayNode().add(a.get(1)).add(a.get(0));assertThat(project(a).semanticHash()).isNotEqualTo(project(b).semanticHash());
        ArrayNode c=scene("COMPOSED");((ObjectNode)c.get(0)).putArray("blocks").add(json.createObjectNode().put("id","b").put("kind","choice").put("revealAt","sau câu hỏi"));
        ArrayNode d=c.deepCopy();((ObjectNode)d.get(0).path("blocks").get(0)).put("revealAt","trước câu hỏi");assertThat(project(c).semanticHash()).isNotEqualTo(project(d).semanticHash());
    }
    @Test void numericCaptionAlignmentAndNewSameTokenAlignmentKeepTheSupportedContract(){
        ArrayNode a=scene("TITLE_HERO"),b=a.deepCopy();ArrayNode words=((ObjectNode)b.get(0)).putArray("captionWords");
        String[] tokens={"không","đổi","đơn","vị"};for(int i=0;i<tokens.length;i++)words.add(json.createObjectNode().put("text",tokens[i]).put("start",i*.5).put("end",i*.5+.4));
        assertThat(fp.compatible(a,b,project(a),project(b))).isTrue();
        a=b.deepCopy();((ObjectNode)b.get(0)).put("durationInFrames",210);
        for(JsonNode word:b.get(0).path("captionWords")){((ObjectNode)word).put("start",word.path("start").asDouble()*1.1).put("end",word.path("end").asDouble()*1.1);}
        assertThat(project(a).semanticHash()).isEqualTo(project(b).semanticHash());assertThat(project(a).presentationContractHash()).isEqualTo(project(b).presentationContractHash());assertThat(fp.compatible(a,b,project(a),project(b))).isTrue();
    }
    @Test void dynamicCueTemplatesNeverInheritOnOnlyLongerDuration(){
        ArrayNode a=scene("COMPOSED"),b=a.deepCopy();((ObjectNode)b.get(0)).put("durationInFrames",300);
        assertThat(project(a).classification()).isEqualTo("UNKNOWN");assertThat(fp.compatible(a,b,project(a),project(b))).isFalse();
    }
}
