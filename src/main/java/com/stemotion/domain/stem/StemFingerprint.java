package com.stemotion.domain.stem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

/** Versioned, server-owned projections. Technical compatibility is not scientific verification. */
public final class StemFingerprint {
    public static final String VERSION = "stem-hash.v1";
    public static final String CANONICAL = "stem-canonical.v1";
    public static final String PROJECTOR = "stem-projector.v1";
    public static final String CLASSIFIER = "stem-classifier.v1";
    private final ObjectMapper json;
    public StemFingerprint(ObjectMapper mapper) {
        json = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }
    public JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception e) { throw StemValidator.invalid("projection", "Invalid JSON projection"); }
    }
    public String canonical(JsonNode value) {
        StringBuilder output = new StringBuilder();
        encode(value, output, 0, new int[]{0});
        if (output.toString().getBytes(StandardCharsets.UTF_8).length > 16 * 1024 * 1024)
            throw StemValidator.invalid("projection", "Projection exceeds 16MiB");
        return output.toString();
    }
    private void encode(JsonNode n, StringBuilder out, int depth, int[] nodes) {
        if (depth > 40 || ++nodes[0] > 20000) throw StemValidator.invalid("projection", "Projection complexity limit");
        if (n == null || n.isNull()) { out.append("null"); return; }
        if (n.isObject()) {
            SortedMap<String, JsonNode> fields = new TreeMap<>(StemFingerprint::codepointOrder);
            n.fields().forEachRemaining(e -> {
                String key = nfc(e.getKey());
                if (fields.putIfAbsent(key,e.getValue()) != null)
                    throw StemValidator.invalid("projection", "Duplicate key after NFC");
            });
            out.append('{'); boolean first=true;
            for (var e:fields.entrySet()) {
                if(!first)out.append(','); first=false;
                quote(e.getKey(),out);out.append(':');encode(e.getValue(),out,depth+1,nodes);
            }
            out.append('}');
        } else if(n.isArray()) {
            out.append('[');boolean first=true;
            for(JsonNode child:n){if(!first)out.append(',');first=false;encode(child,out,depth+1,nodes);}out.append(']');
        } else if(n.isTextual()) quote(nfc(n.textValue()),out);
        else if(n.isBoolean())out.append(n.booleanValue());
        else if(n.isNumber()) {
            BigDecimal number;
            try { number=new BigDecimal(n.asText()); }
            catch(NumberFormatException ex){throw StemValidator.invalid("projection", "Number must be finite");}
            if(number.precision()>512 || Math.abs((long)number.scale())>1536 ||
                Math.abs((long)number.precision()-number.scale()-1)>1024)
                throw StemValidator.invalid("projection","Number precision/exponent limit");
            String text=number.signum()==0?"0":number.stripTrailingZeros().toPlainString();
            if(text.length()>2048)throw StemValidator.invalid("projection","Number length limit");out.append(text);
        } else throw StemValidator.invalid("projection","Unsupported JSON scalar");
        if(out.length()>16*1024*1024)throw StemValidator.invalid("projection","Projection size limit");
    }
    private static int codepointOrder(String a,String b) {
        var x=a.codePoints().iterator();var y=b.codePoints().iterator();
        while(x.hasNext()&&y.hasNext()){int c=Integer.compare(x.nextInt(),y.nextInt());if(c!=0)return c;}
        return x.hasNext()?1:y.hasNext()?-1:0;
    }
    private static String nfc(String s) {
        for(int i=0;i<s.length();i++)if(Character.isSurrogate(s.charAt(i))) {
            if(!Character.isHighSurrogate(s.charAt(i))||i+1>=s.length()||!Character.isLowSurrogate(s.charAt(++i)))
                throw StemValidator.invalid("projection","Unpaired surrogate");
        }
        if(s.codePointCount(0,s.length())>65536)throw StemValidator.invalid("projection","String length limit");
        return Normalizer.normalize(s,Normalizer.Form.NFC);
    }
    private static void quote(String s,StringBuilder out) {
        out.append('"');
        s.codePoints().forEach(c->{if(c=='"')out.append("\\\"");else if(c=='\\')out.append("\\\\");
            else if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",c));else out.appendCodePoint(c);});
        out.append('"');
    }
    public String hash(String projection, JsonNode value) {
        if(!Set.of("semantic","presentationContract","renderInput").contains(projection))throw new IllegalArgumentException("projection");
        String prefix=String.join("\0","STEMotion/P5",projection,VERSION,CANONICAL,PROJECTOR,CLASSIFIER,"");
        return sha(prefix+canonical(value));
    }
    public static String sha(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public record Result(String semanticHash,String presentationContractHash,String classification,JsonNode semantic,JsonNode presentation) {}
    private static final Set<String> COMMON=Set.of("id","type","title","narration","durationInFrames","captionWords","narrationAudioUrl",
        "customFontSize","fontSizeScale","cardScale","cardTheme","cardWidth","componentLayouts","extraBlocks","visualPlan");
    private static final Set<String> CONDITIONAL=Set.of("customFontSize","fontSizeScale","cardScale","cardTheme","cardWidth","componentLayouts","visualPlan");
    public static final Set<String> BLOCKS=Set.of("title","text","list","formula","icon","icons","number","chart","choice","arrow","highlight","guide");
    private static final Map<String,Set<String>> SCENES=Map.ofEntries(
        Map.entry("TITLE_HERO",Set.of("subtitle","subject","gradeLevel","badgeText")),
        Map.entry("MATH_FORMULA",Set.of("latex","steps")),
        Map.entry("DIAGRAM_EXPLAINER",Set.of("diagramTitle","labels","svgType")),
        Map.entry("DATA_CHART",Set.of("chartType","xAxisLabel","yAxisLabel","dataPoints")),
        Map.entry("ALGORITHM_WALKTHROUGH",Set.of("language","codeSnippet","layoutSplit","steps")),
        Map.entry("STEM_QUIZ",Set.of("question","options","correctIndex","hint","explanation")),
        Map.entry("OUTRO",Set.of("summaryPoints","nextLessonSuggestion","instructorName")),
        Map.entry("CHEMICAL_REACTION",Set.of("equation","reactants","products","condition","observation","flaskColor")),
        Map.entry("COMPARISON_SPLIT",Set.of("topicA","topicB","conclusion")),
        Map.entry("PROCESS_TIMELINE",Set.of("processTitle","stages")),
        Map.entry("GEOMETRY_SPACE",Set.of("shapeType","theoremName","formulaLatex","dimensions","explanation")),
        Map.entry("ILLUSTRATED_EXPLAINER",Set.of("headline","caption","ambience","layout","detail","icons")),
        Map.entry("COMPOSED",Set.of("frame","ambience","blocks")));
    public static Set<String> sceneTypes(){return SCENES.keySet();}
    private static final Set<String> SET_FIELDS=Set.of("subjectKeys","outcomeIds","distributionChannels","objectiveKeys","taskKeys");
    public JsonNode normalizedSets(JsonNode node) {
        if(node.isObject()) {
            ObjectNode result=json.createObjectNode();
            node.fields().forEachRemaining(e->{
                if(SET_FIELDS.contains(e.getKey())&&e.getValue().isArray()) {
                    TreeMap<String,JsonNode> sorted=new TreeMap<>(StemFingerprint::codepointOrder);
                    for(JsonNode n:e.getValue()){
                        if(!n.isTextual()||sorted.putIfAbsent(nfc(n.asText()),TextNode.valueOf(nfc(n.asText())))!=null)
                            throw StemValidator.invalid(e.getKey(),"Invalid/duplicate set reference");
                    }
                    result.set(e.getKey(),json.valueToTree(sorted.values()));
                }else result.set(e.getKey(),normalizedSets(e.getValue()));
            });return result;
        }
        if(node.isArray()){ArrayNode a=json.createArrayNode();node.forEach(n->a.add(normalizedSets(n)));return a;}
        return node.deepCopy();
    }
    public Result project(JsonNode scenes,JsonNode brief,JsonNode editions) {
        if(!scenes.isArray()||scenes.size()>200)throw StemValidator.invalid("scenes","Expected <=200 scenes");
        ObjectNode semantic=json.createObjectNode();semantic.set("brief",normalizedSets(brief));semantic.set("editions",editions.deepCopy());
        ArrayNode ss=semantic.putArray("scenes"), pp=json.createArrayNode();boolean[] unknown={false};
        Map<String,Integer> sceneIds=new HashMap<>();
        for(int i=0;i<scenes.size();i++)if(sceneIds.put(scenes.get(i).path("id").asText(),i)!=null)unknown[0]=true;
        // Generated evidence IDs do not define meaning; relationships do.
        normalizeEvidence(semantic,sceneIds,unknown);
        for(JsonNode scene:scenes) {
            if(!scene.isObject())throw StemValidator.invalid("scenes","Scene must be object");
            ObjectNode s=json.createObjectNode(),p=json.createObjectNode();String type=scene.path("type").asText();
            Set<String> keys=SCENES.get(type);if(keys==null){unknown[0]=true;keys=Set.of();}
            // Only these static template contracts have the implemented numeric timing proof.
            // All13 types are projected, but dynamic templates need renderer/visibility evidence.
            if(!Set.of("TITLE_HERO","OUTRO").contains(type))unknown[0]=true;
            Set<String> known=keys;
            nestedFields(scene,"labels",Set.of("name","description","xPercent","yPercent"),unknown);
            nestedFields(scene,"dataPoints",Set.of("label","value","color"),unknown);
            nestedFields(scene,"icons",Set.of("name","label"),unknown);
            nestedFields(scene,"stages",Set.of("stageNumber","title","description","badge"),unknown);
            nestedFields(scene,"steps",type.equals("MATH_FORMULA")?Set.of("label","latexSnippet","explanation"):Set.of("lineHighlight","variableState","note"),unknown);
            nestedFields(scene,"topicA",Set.of("title","badge","points","color"),unknown);
            nestedFields(scene,"topicB",Set.of("title","badge","points","color"),unknown);
            nestedFields(scene,"dimensions",Set.of("a","b","c"),unknown);
            scene.fields().forEachRemaining(e->{String key=e.getKey();JsonNode val=e.getValue();
                if(key.equals("id"))return;
                if(key.equals("captionWords")){
                    ArrayNode words=s.putArray(key);if(!val.isArray())unknown[0]=true;else val.forEach(w->{
                        words.add(w.path("text"));
                        if(!w.isObject()||!w.path("text").isTextual())unknown[0]=true;
                        w.fieldNames().forEachRemaining(k->{if(!Set.of("text","start","end").contains(k)){unknown[0]=true;s.set("unclassifiedCaption",val);}});
                    });
                }else if(key.equals("durationInFrames")){/* numeric timing belongs to bounded compatibility proof */}
                else if(key.equals("narrationAudioUrl")){p.set(key,val);unknown[0]=true;}
                else if(CONDITIONAL.contains(key)){p.set(key,val);if(!val.isNull()&&(!val.isContainerNode()||!val.isEmpty()))unknown[0]=true;}
                else if(key.equals("blocks")||key.equals("extraBlocks")){
                    s.set(key,blocks(val,unknown));p.set(key,val);
                }else {s.set(key,val);if(!COMMON.contains(key)&&!known.contains(key))unknown[0]=true;}
            });
            if(!s.has("captionWords")){
                ArrayNode words=s.putArray("captionWords");String narration=scene.path("narration").asText();
                if(!narration.isBlank())for(String word:narration.strip().split("\\s+"))words.add(word);
            }
            ss.add(s);pp.add(p);
        }
        String sh=hash("semantic",semantic);ObjectNode contract=json.createObjectNode().put("semanticHash",sh);contract.set("presentation",pp);
        return new Result(sh,hash("presentationContract",contract),unknown[0]?"UNKNOWN":"CLASSIFIED",semantic,contract);
    }
    private void nestedFields(JsonNode root,String key,Set<String> known,boolean[] unknown){
        if(!root.hasNonNull(key))return;JsonNode value=root.get(key);
        if(value.isArray()){for(JsonNode child:value)checkFields(child,known,unknown);}
        else checkFields(value,known,unknown);
    }
    private void checkFields(JsonNode node,Set<String> known,boolean[] unknown){
        if(!node.isObject()){unknown[0]=true;return;}
        node.fieldNames().forEachRemaining(k->{if(!known.contains(k))unknown[0]=true;});
    }
    private void normalizeEvidence(ObjectNode semantic,Map<String,Integer> sceneIds,boolean[] unknown) {
        JsonNode ev=semantic.path("brief").path("evidence");
        if(!ev.isArray())return;
        for(JsonNode entry:ev)if(entry.isObject()){
            ObjectNode e=(ObjectNode)entry;e.remove("id");
            if(e.hasNonNull("sceneId")){Integer pos=sceneIds.get(e.path("sceneId").asText());if(pos==null)unknown[0]=true;else e.put("sceneId",pos);}
        }
    }
    private JsonNode blocks(JsonNode blocks,boolean[] unknown) {
        if(!blocks.isArray()||blocks.size()>12)throw StemValidator.invalid("blocks","Expected <=12 blocks");
        if(!blocks.isEmpty())unknown[0]=true; // No arbitrary cue/layout visibility proof is claimed.
        ArrayNode result=json.createArrayNode();Map<String,Integer> ids=new HashMap<>();
        for(int i=0;i<blocks.size();i++)if(ids.put(blocks.get(i).path("id").asText(),i)!=null)unknown[0]=true;
        Set<String> fields=Set.of("id","kind","slot","text","items","latex","icon","icons","value","unit","points","question","options","answer","from","to","pose","at","until","revealAt","strikeAt","motion","emphasis","tone","x","y","w","h","scale","fontSize","hidden");
        for(JsonNode raw:blocks){
            if(!raw.isObject())throw StemValidator.invalid("blocks","Block must be object");
            ObjectNode b=raw.deepCopy();b.remove("id");
            nestedFields(b,"icon",Set.of("name","label"),unknown);nestedFields(b,"icons",Set.of("name","label"),unknown);nestedFields(b,"points",Set.of("label","value"),unknown);
            if(!BLOCKS.contains(b.path("kind").asText()))unknown[0]=true;
            b.fieldNames().forEachRemaining(k->{if(!fields.contains(k))unknown[0]=true;});
            for(String k:List.of("from","to"))if(b.hasNonNull(k)){
                Integer target=ids.get(b.path(k).asText());if(target==null)unknown[0]=true;else b.put(k,target);
            }
            // All conditional block properties remain hashed. No claim of arbitrary pixel proof.
            if(b.has("hidden")||b.has("x")||b.has("y")||b.has("motion"))unknown[0]=true;
            result.add(b);
        }return result;
    }
    /** Safe numeric alignment subset; no arbitrary layout/asset/audio or shorter scene inheritance. */
    public boolean compatible(JsonNode before,JsonNode after,Result oldHash,Result newHash) {
        if(!"CLASSIFIED".equals(oldHash.classification())||!"CLASSIFIED".equals(newHash.classification())||
            !oldHash.semanticHash().equals(newHash.semanticHash())||!oldHash.presentationContractHash().equals(newHash.presentationContractHash())||before.size()!=after.size())return false;
        for(int i=0;i<before.size();i++){
            JsonNode a=before.get(i),b=after.get(i);
            if(!a.path("id").equals(b.path("id"))||b.path("durationInFrames").asInt()<a.path("durationInFrames").asInt()||!validTimes(b))return false;
        }return true;
    }
    private boolean validTimes(JsonNode scene){
        double last=0, seconds=scene.path("durationInFrames").asDouble()/30;
        if(!Double.isFinite(seconds)||seconds<=0)return false;
        for(JsonNode w:scene.path("captionWords")){
            double start=w.path("start").asDouble(Double.NaN),end=w.path("end").asDouble(Double.NaN);
            if(!Double.isFinite(start)||!Double.isFinite(end)||start<last||end<start||end>seconds)return false;last=end;
        }return true;
    }
}
