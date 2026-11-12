package com.stemotion.domain.stem;
import com.fasterxml.jackson.databind.*;
import com.stemotion.core.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.stemotion.domain.stem.StemDtos.*;
@Component
public class StemValidator {
 private final ObjectMapper json;
 public StemValidator(ObjectMapper json){this.json=json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);}
 public static ApiException invalid(String field,String message){return new ApiException(HttpStatus.BAD_REQUEST,"STEM_INVALID_FIELD",message,Map.of("field",field));}
 public <T>T parse(JsonNode node,Class<T> type){try{if(node==null||!node.isObject())throw invalid("request","Expected JSON object");limits(node,0);return json.treeToValue(node,type);}catch(ApiException e){throw e;}catch(Exception e){throw invalid("request","Malformed or unknown STEM field");}}
 public void limits(JsonNode n,int depth){if(n==null||depth>16)throw invalid("request","Maximum JSON depth16");if(depth==0&&n.toString().getBytes(StandardCharsets.UTF_8).length>262144)throw invalid("request","Maximum body262144bytes");if(n.isTextual()&&n.textValue().length()>4000)throw invalid("request","Maximum text4000");if(n.isNumber()&&!Double.isFinite(n.doubleValue()))throw invalid("request","Number must be finite");if(n.isArray()&&n.size()>200)throw invalid("request","Maximum array200");if(n.isContainerNode())n.forEach(c->limits(c,depth+1));}
 public void key(String s,String field){if(s==null||s.isBlank()||s.length()>128)throw invalid(field,"Required key, maximum128");}
 public void rev(Integer n,String field){if(n==null||n<0)throw invalid(field,"Required nonnegative revision");}
 public void schema(String s){if(!"stem.v1".equals(s))throw invalid("schemaVersion","Expected stem.v1");}
 public void idem(String s){if(s==null||!s.matches("[A-Za-z0-9_.:-]{8,128}"))throw invalid("idempotencyKey","Expected8-128 ASCII key");}
 public <T>void list(List<T> a,int max,String field){if(a==null||a.size()>max||a.stream().anyMatch(Objects::isNull))throw invalid(field,"Required list, maximum"+max);}
 private final Set<String> contexts=Set.of("IN_CLASS_SCREEN","GROUP_PRACTICE");
 private final Set<String> components=Set.of("S","T","E","M");
 public List<Finding> check(Brief b,List<Evidence> evidence){
  if(b==null||b.context()==null)throw invalid("brief.context","Required context object");
  list(b.subjectKeys(),20,"subjectKeys");list(b.outcomeIds(),20,"outcomeIds");list(b.context().secondary(),4,"context.secondary");list(b.distributionChannels(),10,"distributionChannels");list(b.objectives(),20,"objectives");list(b.studentTasks(),20,"studentTasks");list(b.contributions(),4,"contributions");list(evidence,200,"evidence");
  for(String id:b.subjectKeys())key(id,"subjectKeys");for(String id:b.outcomeIds())key(id,"outcomeIds");
  if(new HashSet<>(b.subjectKeys()).size()!=b.subjectKeys().size()||new HashSet<>(b.outcomeIds()).size()!=b.outcomeIds().size())throw invalid("brief","Duplicate subject/outcome reference");
  if(b.gradeKey()!=null)key(b.gradeKey(),"gradeKey");
  for(Evidence e:evidence){if(e.id()!=null)key(e.id(),"evidence.id");if(e.sceneId()!=null)key(e.sceneId(),"evidence.sceneId");}
  List<Finding> f=new ArrayList<>();
  if(b.context().primary()==null)f.add(new Finding("TECH-CONTEXT","brief.context.primary","ERROR","Primary context is incomplete"));
  Set<String> seen=new HashSet<>();if(b.context().primary()!=null){context(b.context().primary());seen.add(b.context().primary().key());}
  for(ContextEntry c:b.context().secondary()){context(c);if(!seen.add(c.key()))f.add(new Finding("TECH-CONTEXT","brief.context.secondary","ERROR","Duplicate context"));if(c.condition()==null||c.condition().isBlank()||c.adaptations()==null||c.adaptations().isBlank())f.add(new Finding("TECH-CONTEXT","brief.context.secondary","ERROR","Secondary needs condition/adaptations"));}
  if(b.distributionChannels().stream().anyMatch(c->!"DEPARTMENT_LIBRARY".equals(c)))throw invalid("distributionChannels","Unknown distribution channel");
  if(new HashSet<>(b.distributionChannels()).size()!=b.distributionChannels().size())throw invalid("distributionChannels","Duplicate channel");
  Set<String> objectives=new HashSet<>(),tasks=new HashSet<>(),cs=new HashSet<>();
  for(Objective o:b.objectives()){key(o.key(),"objectives.key");if(!objectives.add(o.key()))throw invalid("objectives","Duplicate objective key");if(o.outcomeId()!=null&&!b.outcomeIds().contains(o.outcomeId()))throw invalid("objectives.outcomeId","Outcome not selected in brief");}
  for(Task t:b.studentTasks()){key(t.key(),"studentTasks.key");if(!tasks.add(t.key()))throw invalid("studentTasks","Duplicate task key");if(t.executionContextKey()!=null&&!seen.contains(t.executionContextKey()))throw invalid("studentTasks.executionContextKey","Context not in brief");}
  for(Contribution c:b.contributions()){if(!components.contains(Objects.toString(c.component(),""))||!cs.add(c.component()))throw invalid("contributions","Unknown/duplicate component");list(c.objectiveKeys(),20,"contributions.objectiveKeys");list(c.taskKeys(),20,"contributions.taskKeys");if(!objectives.containsAll(c.objectiveKeys())||!tasks.containsAll(c.taskKeys()))throw invalid("contributions","Unknown task/objective reference");}
  Set<String> ids=new HashSet<>();
  for(Evidence e:evidence){if(e==null||e.component()==null||!components.contains(e.component()))throw invalid("evidence.component","Expected S/T/E/M");if(e.id()!=null&&!ids.add(e.id()))throw invalid("evidence.id","Duplicate evidence");if(e.sceneId()==null&&e.taskKey()==null)throw invalid("evidence","Scene or task required");if(e.taskKey()!=null&&!tasks.contains(e.taskKey()))throw invalid("evidence.taskKey","Unknown task");if(e.objectiveKey()!=null&&!objectives.contains(e.objectiveKey()))throw invalid("evidence.objectiveKey","Unknown objective");}
  if(b.objectives().isEmpty())f.add(new Finding("TECH-OBJECTIVE","brief.objectives","ERROR","Learner objective is incomplete"));
  for(Objective o:b.objectives())if(blank(o.learnerAction())||blank(o.observableEvidence())||o.outcomeId()==null)f.add(new Finding("TECH-OBJECTIVE","brief.objectives."+o.key(),"ERROR","Objective needs outcome/action/observable evidence"));
  if(b.studentTasks().isEmpty())f.add(new Finding("TECH-TASK","brief.studentTasks","ERROR","Student task is incomplete"));
  for(Task task:b.studentTasks())if(blank(task.learnerAction())||blank(task.teacherSupport())||blank(task.materials())||blank(task.safety())||blank(task.assessmentEvidence())||blank(task.executionContextKey()))f.add(new Finding("TECH-TASK","brief.studentTasks."+task.key(),"ERROR","Task/support/materials/safety/assessment/context incomplete"));
  if(blank(b.scopeOfClip())||blank(b.safetyNotes()))f.add(new Finding("TECH-SCOPE","brief","ERROR","Scope and safety notes incomplete"));
  if(b.contributions().isEmpty())f.add(new Finding("TECH-CONTRIBUTION","brief.contributions","WARN","Contributions require human review; no subject-count threshold"));
  if(b.gradeKey()==null||b.policyVersionId()==null||b.outcomeIds().isEmpty())f.add(new Finding("TECH-CATALOG","brief","ERROR","Catalog/grade/outcome incomplete"));
  f.add(new Finding("TECH-PROVENANCE","brief","WARN","DRAFT/UNVERIFIED: no scientific verification"));return f;
 }
 private boolean blank(String s){return s==null||s.isBlank();}
 private void context(ContextEntry c){if(c.key()==null||!contexts.contains(c.key()))throw invalid("context.key","Unknown learning context; channels are separate");}
 /** Pure evaluator only. No runtime path persists STRICT or claims scientific correctness. */
 public void transition(String mode,List<Finding> findings,boolean assessmentCurrent){if("STRICT".equals(mode)&&(findings.stream().anyMatch(f->"ERROR".equals(f.severity()))||!assessmentCurrent))throw new ApiException(HttpStatus.BAD_REQUEST,"STEM_VALIDATION_FAILED","STEM prerequisites not satisfied",Map.of("findings",findings));}
}
