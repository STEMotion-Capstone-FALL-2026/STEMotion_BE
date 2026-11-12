package com.stemotion.domain.stem;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static com.stemotion.domain.stem.StemDtos.*;
@Service
@ConditionalOnProperty(name="app.stem.enabled",havingValue="true")
@Transactional
public class StemService {
 private final StemRepository db; private final StemValidator validator;private final WorkspaceAccess access;private final ScriptVersionRepository versions;private final ClipProjectRepository projects;
 private final StemFingerprint fingerprint;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 @org.springframework.beans.factory.annotation.Autowired(required=false) private StemArtifactService artifacts;
 public StemService(StemRepository db,StemValidator validator,WorkspaceAccess access,ScriptVersionRepository versions,ClipProjectRepository projects){this.db=db;this.validator=validator;this.access=access;this.versions=versions;this.projects=projects;this.fingerprint=new StemFingerprint(db.json());}
 private void revision(Integer expected,int actual,String field){validator.rev(expected,field);if(expected!=actual)throw ApiException.staleRevision(field,actual,expected,"STEM target changed; reload");}
 private ScriptVersion target(String project,String version,String actor,boolean write){
  access.requireProject(project,actor,false);
  ScriptVersion v=versions.findById(version).orElseThrow(()->ApiException.notFound("ScriptVersion",version));
  if(!v.getProject().getId().equals(project))throw ApiException.forbidden("Version is outside project");
  if(write)access.requireActiveStemSeat(v.getProject().getWorkspace().getId(),actor,WorkspaceRole.WRITER);
  return v;
 }
 private void lock(ScriptVersion v){
  var row=db.jdbc().queryForList("select id from script_versions where id=? for update",v.getId());if(row.isEmpty())throw ApiException.conflict("Version changed");
  if(em!=null){em.refresh(v);em.refresh(v.getProject());}
  db.jdbc().queryForList("select id from workspaces where id=? for update",v.getProject().getWorkspace().getId());
 }
 private void editable(ScriptVersion v){if(Boolean.TRUE.equals(v.getProject().getArchived())||!Boolean.TRUE.equals(v.getIsCurrent())||!(v.getStatus()==com.stemotion.core.enums.StemEnums.ScriptStatus.DRAFT||v.getStatus()==com.stemotion.core.enums.StemEnums.ScriptStatus.CHANGE_REQUESTED))throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"STEM_IMMUTABLE","Submitted brief is immutable");}
 private String digest(Object request){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(db.encode(request).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private String receipt(String actor,String op,String target,String key,Object payload){
  validator.idem(key);db.jdbc().update("delete from stem_receipts where actor_id=? and operation=? and request_key=? and expires_at<=current_timestamp",actor,op,key);
  var rows=db.jdbc().queryForList("select * from stem_receipts where actor_id=? and operation=? and request_key=?",actor,op,key);
  if(rows.isEmpty())return null;var r=rows.get(0);if(!target.equals(db.text(r,"target"))||!digest(payload).equals(db.text(r,"request_digest")))throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"STEM_IDEMPOTENCY_CONFLICT","Key reused with another payload or target");return db.text(r,"response");
 }
 private void remember(String actor,String op,String target,String key,Object payload,Object result){db.jdbc().update("insert into stem_receipts(actor_id,operation,target,request_key,request_digest,response,expires_at) values(?,?,?,?,?,?::jsonb,current_timestamp+interval '24 hours')",actor,op,target,key,digest(payload),db.encode(result));}
 public View get(String project,String version,String actor){target(project,version,actor,false);return view(version);}
 public View create(String project,String version,String actor,Create request){validator.limits(db.json().valueToTree(request),0);
  validator.schema(request.schemaVersion());validator.rev(request.expectedScriptRevision(),"expectedScriptRevision");var v=target(project,version,actor,true);lock(v);
  String replay=receipt(actor,"CREATE",version,request.idempotencyKey(),request);if(replay!=null)return db.decode(replay,View.class);
  editable(v);revision(request.expectedScriptRevision(),v.getDraftRevision(),"SCRIPT_DRAFT");if(db.brief(version)!=null)throw ApiException.conflict("Brief already exists");
  db.jdbc().update("insert into stem_briefs(id,version_id,payload) values(?,?,?::jsonb)",db.id(),version,db.encode(StemDtos.empty()));
  View result=view(version);remember(actor,"CREATE",version,request.idempotencyKey(),request,result);db.audit(actor,version,"BRIEF_CREATED",Map.of("revision",0));return result;
 }
 public View save(String project,String version,String actor,Save request){validator.limits(db.json().valueToTree(request),0);
  validator.schema(request.schemaVersion());validator.rev(request.baseRevision(),"baseRevision");validator.rev(request.expectedScriptRevision(),"expectedScriptRevision");
  var v=target(project,version,actor,true);lock(v);String replay=receipt(actor,"SAVE",version,request.idempotencyKey(),request);if(replay!=null)return db.decode(replay,View.class);
  editable(v);revision(request.expectedScriptRevision(),v.getDraftRevision(),"SCRIPT_DRAFT");var row=requiredBrief(version);revision(request.baseRevision(),db.number(row,"revision"),"STEM_BRIEF");
  validator.check(request.brief(),request.evidence());catalogReferences(request.brief(),v.getProject().getWorkspace().getId(),db.decode(db.text(row,"payload"),Brief.class));
  Set<String> existing=new HashSet<>();for(Evidence e:db.evidence(db.text(row,"id")))existing.add(e.id());
  Set<String> allIds=new HashSet<>();for(Evidence e:request.evidence()){
   if(e.id()!=null&&!existing.contains(e.id()))throw StemValidator.invalid("evidence.id","Evidence outside brief");
   if(e.sceneId()!=null){Integer n=db.jdbc().queryForObject("select count(*) from scenes where id=? and version_id=?",Integer.class,e.sceneId(),version);if(n!=1){boolean oldOrphan=e.id()!=null&&db.jdbc().queryForObject("select count(*) from stem_evidence where id=? and brief_id=? and original_scene_id=? and scene_id is null",Integer.class,e.id(),db.text(row,"id"),e.sceneId())==1;if(!oldOrphan)throw StemValidator.invalid("evidence.sceneId","Scene outside version");}}
  }
  db.jdbc().update("update stem_briefs set payload=?::jsonb,policy_id=?,topic_id=?,revision=revision+1,readiness='NEEDS_REVIEW' where id=?",db.encode(request.brief()),request.brief().policyVersionId(),request.brief().topicVersionId(),db.text(row,"id"));
  db.audit(actor,version,"EVIDENCE_PREVIOUS",db.evidence(db.text(row,"id")));
  db.jdbc().update("update stem_evidence set retired=true where brief_id=?",db.text(row,"id"));
  for(Evidence e:request.evidence()){
   String id=e.id()==null?db.id():e.id();if(!allIds.add(id))throw StemValidator.invalid("evidence.id","Duplicate evidence");
   String live=e.sceneId()!=null&&db.jdbc().queryForObject("select count(*) from scenes where id=? and version_id=?",Integer.class,e.sceneId(),version)==1?e.sceneId():null;
   db.jdbc().update("insert into stem_evidence(id,brief_id,scene_id,original_scene_id,task_key,objective_key,component,explanation) values(?,?,?,?,?,?,?,?) on conflict(id) do update set scene_id=excluded.scene_id,original_scene_id=excluded.original_scene_id,task_key=excluded.task_key,objective_key=excluded.objective_key,component=excluded.component,explanation=excluded.explanation,retired=false",id,db.text(row,"id"),live,e.sceneId(),e.taskKey(),e.objectiveKey(),e.component(),e.explanation());
  }
  invalidate(version);db.audit(actor,version,"BRIEF_REPLACED",Map.of("revision",request.baseRevision()+1));View result=view(version);remember(actor,"SAVE",version,request.idempotencyKey(),request,result);return result;
 }
 private Map<String,Object> requiredBrief(String version){var r=db.brief(version);if(r==null)throw ApiException.notFound("STEM brief",version);return r;}
 private void catalogReferences(Brief b,String workspace,Brief old){
  checkKind(b.topicVersionId(),"TOPIC_VERSION");checkKind(b.policyVersionId(),"POLICY");for(String id:b.outcomeIds())checkKind(id,"OUTCOME");
  if(b.policyVersionId()!=null&&!Objects.equals(b.policyVersionId(),old.policyVersionId())&&!db.settings(workspace).policyIds().contains(b.policyVersionId()))throw StemValidator.invalid("brief.policyVersionId","Policy is not assigned");
  for(String id:b.outcomeIds()){
   var r=db.jdbc().queryForMap("select * from stem_catalog where id=?",id);JsonNode data=db.decode(db.text(r,"data"),JsonNode.class);
   if(data.hasNonNull("gradeKey")&&!Objects.equals(data.path("gradeKey").asText(),b.gradeKey()))throw StemValidator.invalid("brief.outcomeIds","Outcome grade mismatch");
   if(data.hasNonNull("subjectKey")&&!b.subjectKeys().contains(data.path("subjectKey").asText()))throw StemValidator.invalid("brief.subjectKeys","Outcome subject mismatch");
  }
 }
 private void checkKind(String id,String kind){if(id!=null){validator.key(id,kind+"Id");if(db.jdbc().queryForObject("select count(*) from stem_catalog where id=? and kind=?",Integer.class,id,kind)!=1)throw StemValidator.invalid("catalog."+kind,"Missing or wrong catalog kind");}}
 private View view(String version){var v=versions.findById(version).orElseThrow();var mode=db.settings(v.getProject().getWorkspace().getId());var r=db.brief(version);if(r==null)return new View("stem.v1",null,version,0,"UNVERIFIED","LEGACY_UNMAPPED",mode.mode(),mode.revision(),null,List.of(),List.of(),null,null,null);
  Brief b=db.decode(db.text(r,"payload"),Brief.class);var evidence=db.evidence(db.text(r,"id"));var findings=new ArrayList<>(validator.check(b,evidence));
  for(Evidence e:evidence)if(e.sceneId()!=null&&db.jdbc().queryForObject("select count(*) from scenes where id=? and version_id=?",Integer.class,e.sceneId(),version)!=1)findings.add(new Finding("TECH-EVIDENCE","evidence."+e.id(),"ERROR","Orphan scene reference"));
  if(b.policyVersionId()!=null&&!mode.policyIds().contains(b.policyVersionId()))findings.add(new Finding("TECH-POLICY","brief.policyVersionId","ERROR","Pinned policy is unavailable"));
  String scriptFingerprint=computeScriptFingerprint(v.getProject(),v);
  var compRows=db.jdbc().queryForList("select id from compositions where script_version_id=?",version);
  String compositionFingerprint=compRows.isEmpty()?null:computeCompositionFingerprint(v.getProject(),v);
  return new View("stem.v1",db.text(r,"id"),version,db.number(r,"revision"),"UNVERIFIED",b.rawLegacy()!=null&&!b.rawLegacy().isBlank()&&b.context().primary()==null?"LEGACY_UNMAPPED":findings.stream().anyMatch(f->f.severity().equals("ERROR"))?"INCOMPLETE":db.text(r,"readiness"),mode.mode(),mode.revision(),b,evidence,findings,legacy(b),scriptFingerprint,compositionFingerprint);
 }
 private LegacySuggestion legacy(Brief b){
  if(b.rawLegacy()==null||b.rawLegacy().isBlank())return null;
  String value=b.rawLegacy().strip();boolean pending=b.context().primary()==null;
  if(value.equals("DEPARTMENT_LIBRARY"))return new LegacySuggestion(null,List.of(value),pending,"Channel suggestion only; Writer confirms learning context");
  if(Set.of("IN_CLASS_SCREEN","GROUP_PRACTICE").contains(value))return new LegacySuggestion(value,List.of(),pending,"Suggested mapping only; Writer confirms by CAS");
  return new LegacySuggestion(null,List.of(),true,"Unknown/multiple legacy values: Writer choice or separate variant required");
 }
 public StemModels.WorkspaceMode settings(String workspace,String actor){access.requireWorkspace(workspace,actor,false);workspace(workspace);return db.settings(workspace);}
 private void workspace(String id){if(db.jdbc().queryForObject("select count(*) from workspaces where id=?",Integer.class,id)!=1)throw ApiException.notFound("Workspace",id);db.jdbc().queryForList("select id from workspaces where id=? for update",id);}
 public StemModels.WorkspaceMode saveSettings(String workspace,String actor,Settings request){validator.limits(db.json().valueToTree(request),0);
  if(!access.isAdmin(actor))throw ApiException.forbidden("Only Admin assigns STEM framework");workspace(workspace);validator.rev(request.baseRevision(),"baseRevision");validator.list(request.policyVersionIds(),100,"policyVersionIds");
  if(!Set.of("OFF","ADVISORY","STRICT").contains(Objects.toString(request.mode(),"")))throw StemValidator.invalid("mode","Expected OFF/ADVISORY/STRICT");
  String replay=receipt(actor,"SETTINGS",workspace,request.idempotencyKey(),request);if(replay!=null)return db.decode(replay,StemModels.WorkspaceMode.class);
  var old=db.settings(workspace);revision(request.baseRevision(),old.revision(),"STEM_MODE");for(String policy:request.policyVersionIds())checkKind(policy,"POLICY");
  if(new HashSet<>(request.policyVersionIds()).size()!=request.policyVersionIds().size())throw StemValidator.invalid("policyVersionIds","Duplicate policy");
  if(request.defaultPolicyVersionId()!=null&&!request.policyVersionIds().contains(request.defaultPolicyVersionId()))throw StemValidator.invalid("defaultPolicyVersionId","Default must be assigned");
  var legacy=db.jdbc().queryForList("select legacy_modes from stem_settings where workspace_id=?",workspace);
  if(!legacy.isEmpty()&&db.decode(db.text(legacy.get(0),"legacy_modes"),String[].class).length>1&&!request.mode().equals(request.legacyModeSelection()))throw ApiException.conflict("Legacy mode conflict requires explicit selection");
  db.jdbc().update("insert into stem_settings(workspace_id,revision,mode,policy_ids,default_policy_id) values(?,?,?,?,?) on conflict(workspace_id) do update set revision=excluded.revision,mode=excluded.mode,policy_ids=excluded.policy_ids,default_policy_id=excluded.default_policy_id,legacy_modes='[]'::jsonb",workspace,old.revision()+1,request.mode(),db.encode(request.policyVersionIds()),request.defaultPolicyVersionId());
  db.audit(actor,workspace,"MODE_ASSIGNED",request);var result=db.settings(workspace);remember(actor,"SETTINGS",workspace,request.idempotencyKey(),request,result);return result;
 }
 public List<StemModels.CatalogVersion> catalog(String actor,String kind,int page,int size,boolean admin){if(admin&&!access.isAdmin(actor))throw ApiException.forbidden("Admin framework view required");if(access.user(actor).isEmpty())throw ApiException.forbidden("Authenticated user required");return db.catalog(kind,page,size);}
 public View validate(String project,String version,String actor,Validate request){validator.limits(db.json().valueToTree(request),0);
  var v=target(project,version,actor,false);lock(v);var r=requiredBrief(version);revision(request.expectedBriefRevision(),db.number(r,"revision"),"STEM_BRIEF");revision(request.expectedScriptRevision(),v.getDraftRevision(),"SCRIPT_DRAFT");phase(request.phase());
  db.jdbc().update("insert into stem_validation_limits(actor_id,version_id,bucket,requests) values(?,?,floor(extract(epoch from current_timestamp)/60),1) on conflict(actor_id,version_id) do update set requests=case when stem_validation_limits.bucket=excluded.bucket then stem_validation_limits.requests+1 else 1 end,bucket=excluded.bucket",actor,version);
  int count=db.jdbc().queryForObject("select requests from stem_validation_limits where actor_id=? and version_id=?",Integer.class,actor,version);if(count>10)throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"STEM_RATE_LIMIT","Validation limit10/minute",Map.of("retryAfterSeconds",60));
  if("VIDEO".equals(request.phase()))compositionRevision(version,request.expectedCompositionRevision());View result=view(version);db.jdbc().update("insert into stem_validation_runs(id,brief_id,phase,brief_revision,script_revision,mode_revision,findings) values(?,?,?,?,?,?,?::jsonb)",db.id(),db.text(r,"id"),request.phase(),result.revision(),v.getDraftRevision(),result.modeRevision(),db.encode(result.findings()));return result;
 }
 private void phase(String phase){if(!Set.of("SCRIPT","VIDEO").contains(Objects.toString(phase,"")))throw StemValidator.invalid("phase","Expected SCRIPT/VIDEO");}
 private void compositionRevision(String version,Integer expected){validator.rev(expected,"expectedCompositionRevision");var r=db.jdbc().queryForList("select revision from compositions where script_version_id=? for update",version);if(r.isEmpty())throw ApiException.conflict("Composition missing");revision(expected,db.number(r.get(0),"revision"),"COMPOSITION");}
 public void onScriptMutation(String version){if(db.brief(version)!=null)invalidate(version);}
 private void invalidate(String version){db.jdbc().update("update stem_briefs set readiness='NEEDS_REVIEW' where version_id=?",version);db.jdbc().update("update stem_assessments set freshness='NEEDS_REVIEW' where version_id=?",version);}
 public void submission(ClipProject project,ScriptVersion version){
  lock(version);
  var mode=db.settings(project.getWorkspace().getId());
  if("OFF".equals(mode.mode()))return;
  var r=db.brief(version.getId());
  if(r==null){
   if("STRICT".equals(mode.mode()))validator.transition("STRICT",List.of(new Finding("TECH-BRIEF","brief","ERROR","STEM brief is required in STRICT mode")),true);
   return;
  }
  View v=view(version.getId());
  validator.transition(v.effectiveMode(),v.findings(),true);
 }
 public record ReviewTicket(String actor,String version,String phase,Assessment request,Set<String> previous,boolean replay,String note) {}
 public ReviewTicket prepare(ClipProject project,ScriptVersion version,String actor,String phase,Assessment a){return prepare(project,version,actor,phase,a,null);}
 public ReviewTicket prepare(ClipProject project,ScriptVersion version,String actor,String phase,Assessment a,String note){
  lock(version);access.requireProjectRole(project.getId(),actor,Set.of(WorkspaceRole.REVIEWER));
  var mode=db.settings(project.getWorkspace().getId());
  if("STRICT".equals(mode.mode())){
   access.requireActiveStemSeat(project.getWorkspace().getId(),actor,WorkspaceRole.REVIEWER);
   if(a==null)throw new ApiException(HttpStatus.BAD_REQUEST,"STEM_RUBRIC_REQUIRED","Rubric assessment required in STRICT mode");
   if(a.rubricVersionId()==null||a.rubricVersionId().isBlank())throw StemValidator.invalid("rubricVersionId","Rubric required in STRICT mode");
  }
  if(a==null)return null;
  validator.limits(db.json().valueToTree(a),0);
  access.requireActiveStemSeat(project.getWorkspace().getId(),actor,WorkspaceRole.REVIEWER);
  phase(phase);
  validator.list(a.criteria(),20,"criteria");
  Set<String> criterionKeys=new HashSet<>();
  for(Criterion c:a.criteria()){
   if(!criterionKeys.add(c.key()))throw StemValidator.invalid("criteria.key","Duplicate criterion");
   validator.key(c.key(),"criteria.key");
   if(!Set.of("ADEQUATE","NEEDS_CHANGES","NOT_ASSESSED","NOT_APPLICABLE").contains(Objects.toString(c.judgment(),"")))throw StemValidator.invalid("criteria.judgment","Unknown judgment");
   validator.list(c.evidenceIds(),20,"criteria.evidenceIds");
   if(c.rationale()==null||c.rationale().isBlank())throw StemValidator.invalid("criteria.rationale","Rationale required");
  }
  String replay=receipt(actor,"APPROVE_"+phase,version.getId(),a.idempotencyKey(),Map.of("assessment",a,"note",Objects.toString(note,"")));
  if(replay!=null)return new ReviewTicket(actor,version.getId(),phase,a,Set.of(),true,note);
  var r=requiredBrief(version.getId());
  revision(a.expectedBriefRevision(),db.number(r,"revision"),"STEM_BRIEF");
  revision(a.expectedScriptRevision(),version.getDraftRevision(),"SCRIPT_DRAFT");
  revision(a.expectedModeRevision(),mode.revision(),"STEM_MODE");
  if(phase.equals("VIDEO"))compositionRevision(version.getId(),a.expectedCompositionRevision());
  Set<String> ids=new HashSet<>();db.evidence(db.text(r,"id")).forEach(e->ids.add(e.id()));
  for(Criterion c:a.criteria())if(!ids.containsAll(c.evidenceIds()))throw StemValidator.invalid("criteria.evidenceIds","Evidence outside brief");
  checkKind(a.rubricVersionId(),"RUBRIC");
  if(r.get("policy_id")!=null){
   var pRows=db.jdbc().queryForList("select data from stem_catalog where id=?",db.text(r,"policy_id"));
   if(!pRows.isEmpty()){
    JsonNode pData=db.decode(db.text(pRows.get(0),"data"),JsonNode.class);
    if(pData.hasNonNull("rubricVersionId")&&!Objects.equals(pData.path("rubricVersionId").asText(),a.rubricVersionId()))
     throw StemValidator.invalid("rubricVersionId","Rubric does not match pinned policy");
   }
  }
  String currentFp=phase.equals("SCRIPT")?computeScriptFingerprint(project,version):computeCompositionFingerprint(project,version);
  if("STRICT".equals(mode.mode())){
   if(a.expectedFingerprint()==null||a.expectedFingerprint().isBlank()||!a.expectedFingerprint().equals(currentFp)){
    throw new ApiException(HttpStatus.CONFLICT,"STEM_FINGERPRINT_STALE","Rubric assessment fingerprint does not match target "+phase+": expected="+currentFp+", actual="+a.expectedFingerprint(),Map.of("expected",currentFp,"actual",Objects.toString(a.expectedFingerprint(),"")));
   }
  }else if(a.expectedFingerprint()!=null&&!a.expectedFingerprint().equals(currentFp)){
   if("ADVISORY".equals(mode.mode()))
    db.audit(actor,project.getId(),"STEM_FINGERPRINT_MISMATCH",Map.of("phase",phase,"expected",currentFp,"actual",a.expectedFingerprint()));
  }
  return new ReviewTicket(actor,version.getId(),phase,a,historyIds(project.getId()),false,note);
 }
 private Set<String> historyIds(String project){return new HashSet<>(db.jdbc().queryForList("select id from status_histories where project_id=?",String.class,project));}
 public void finish(ClipProject project,ReviewTicket ticket){
  if(ticket==null||ticket.replay())return;
  if(em!=null)em.flush();
  Set<String> ids=historyIds(project.getId());
  ids.removeAll(ticket.previous());
  if(ids.size()!=1)throw ApiException.conflict("Expected exactly one existing review decision");
  String decision=ids.iterator().next();
  int linked=db.jdbc().queryForObject("select count(*) from status_histories where id=? and changed_by=? and event_type='REVIEW_DECISION' and decision='APPROVED'",Integer.class,decision,ticket.actor());
  if(linked!=1)throw ApiException.conflict("Assessment needs matching existing decision");
  var r=requiredBrief(ticket.version());
  ScriptVersion v=versions.findById(ticket.version()).orElseThrow();
  String currentFp=ticket.phase().equals("SCRIPT")?computeScriptFingerprint(project,v):computeCompositionFingerprint(project,v);
  ObjectNode storedPayload=(ObjectNode)db.json().valueToTree(ticket.request());
  storedPayload.put("fingerprint",currentFp);
  storedPayload.put("phase",ticket.phase());
  storedPayload.put("actorId",ticket.actor());
  db.jdbc().update("insert into stem_assessments(id,version_id,brief_id,decision_id,actor_id,phase,brief_revision,script_revision,mode_revision,composition_revision,payload) values(?,?,?,?,?,?,?,?,?,?,?::jsonb)",
   db.id(),ticket.version(),db.text(r,"id"),decision,ticket.actor(),ticket.phase(),
   ticket.request().expectedBriefRevision(),ticket.request().expectedScriptRevision(),ticket.request().expectedModeRevision(),ticket.request().expectedCompositionRevision(),
   db.encode(storedPayload));
  remember(ticket.actor(),"APPROVE_"+ticket.phase(),ticket.version(),ticket.request().idempotencyKey(),Map.of("assessment",ticket.request(),"note",Objects.toString(ticket.note(),"")),Map.of("decisionId",decision));
  if(artifacts!=null&&ticket.phase().equals("SCRIPT")){
   String assessment=db.jdbc().queryForObject("select id from stem_assessments where decision_id=?",String.class,decision);
   artifacts.scriptApproved(project,ticket.actor(),decision,assessment);
  }
 }
 public record ChangeTicket(String actor,String source,ChangeTarget request,String note,String copiedVersion) {}
 public ChangeTicket beginChange(ClipProject project,String actor,ChangeTarget request,String note){
  access.requireProjectRole(project.getId(),actor,Set.of(WorkspaceRole.REVIEWER));
  ScriptVersion source=versions.findFirstByProjectIdAndIsCurrentTrue(project.getId()).orElseThrow();
  if(request!=null){validator.limits(db.json().valueToTree(request),0);validator.key(request.expectedVersionId(),"expectedVersionId");source=versions.findById(request.expectedVersionId()).orElseThrow(()->ApiException.notFound("ScriptVersion",request.expectedVersionId()));if(!source.getProject().getId().equals(project.getId()))throw ApiException.forbidden("Version outside project");}
  lock(source);if(request==null)return null;
  Object payload=Map.of("target",request,"note",note);String cached=receipt(actor,"CHANGE",source.getId(),request.idempotencyKey(),payload);
  if(cached!=null)return new ChangeTicket(actor,source.getId(),request,note,db.decode(cached,JsonNode.class).path("copiedVersion").asText());
  if(!Boolean.TRUE.equals(source.getIsCurrent()))throw ApiException.conflict("Change source is no longer current");revision(request.expectedScriptRevision(),source.getDraftRevision(),"SCRIPT_DRAFT");revision(request.expectedBriefRevision(),db.number(requiredBrief(source.getId()),"revision"),"STEM_BRIEF");
  return new ChangeTicket(actor,source.getId(),request,note,null);
 }
 public void finishChange(ChangeTicket t,String next){if(t!=null)remember(t.actor(),"CHANGE",t.source(),t.request().idempotencyKey(),Map.of("target",t.request(),"note",t.note()),Map.of("copiedVersion",next));}
 public void copy(String oldVersion,String newVersion,Map<String,String> map){
  if(em!=null)em.flush();var old=db.brief(oldVersion);if(old==null)return;
  Set<String> source=new HashSet<>(db.jdbc().queryForList("select id from scenes where version_id=?",String.class,oldVersion));Set<String> destination=new HashSet<>(db.jdbc().queryForList("select id from scenes where version_id=?",String.class,newVersion));
  if(!map.keySet().equals(source)||new HashSet<>(map.values()).size()!=map.size()||!destination.equals(new HashSet<>(map.values())))throw ApiException.conflict("Incomplete/nonbijective copy map");
  String brief=db.id();db.jdbc().update("insert into stem_briefs(id,version_id,payload,policy_id,topic_id,copied_from,readiness) values(?,?,?::jsonb,?,?,?,'NEEDS_REVIEW')",brief,newVersion,db.text(old,"payload"),db.text(old,"policy_id"),db.text(old,"topic_id"),db.text(old,"id"));
  for(Evidence e:db.evidence(db.text(old,"id"))){if(e.sceneId()!=null&&!map.containsKey(e.sceneId()))throw ApiException.conflict("Orphan evidence cannot be copied without correction");String scene=e.sceneId()==null?null:map.get(e.sceneId());db.jdbc().update("insert into stem_evidence(id,brief_id,scene_id,original_scene_id,task_key,objective_key,component,explanation,copied_from) values(?,?,?,?,?,?,?,?,?)",db.id(),brief,scene,scene,e.taskKey(),e.objectiveKey(),e.component(),e.explanation(),e.id());}
 }
 public String computeScriptFingerprint(ClipProject project,ScriptVersion version){
  ArrayNode scenes=db.json().createArrayNode();
  for(var row:db.jdbc().queryForList("select * from scenes where version_id=? order by order_no",version.getId())){
   ObjectNode scene=(ObjectNode)db.decode(db.text(row,"props"),JsonNode.class);
   scene.put("id",db.text(row,"id"));
   scene.put("type",db.text(row,"scene_type"));
   scene.put("title",db.text(row,"title"));
   scene.put("narration",db.text(row,"narration"));
   scene.put("durationInFrames",db.number(row,"duration_in_frames"));
   scenes.add(scene);
  }
  var brief=db.brief(version.getId());
  ObjectNode briefData=db.json().createObjectNode();
  ArrayNode editions=db.json().createArrayNode();
  if(brief!=null){
   briefData.set("brief",db.decode(db.text(brief,"payload"),JsonNode.class));
   briefData.set("evidence",db.json().valueToTree(db.evidence(db.text(brief,"id"))));
  }
  ObjectNode metadata=db.json().createObjectNode()
   .put("title",project.getTitle())
   .put("subject",project.getSubject().name())
   .put("gradeLevel",project.getGradeLevel());
  briefData.set("publicMetadata",metadata);
  var projected=fingerprint.project(scenes,briefData,editions);
  return projected.semanticHash();
 }
 public String computeCompositionFingerprint(ClipProject project,ScriptVersion version){
  var compRows=db.jdbc().queryForList("select * from compositions where script_version_id=?",version.getId());
  Map<String,Object> composition=compRows.isEmpty()?null:compRows.get(0);
  ArrayNode scenes=db.json().createArrayNode();
  if(composition!=null&&composition.get("scenes")!=null){
   scenes=(ArrayNode)db.decode(db.text(composition,"scenes"),JsonNode.class);
  }else{
   for(var row:db.jdbc().queryForList("select * from scenes where version_id=? order by order_no",version.getId())){
    ObjectNode scene=(ObjectNode)db.decode(db.text(row,"props"),JsonNode.class);
    scene.put("id",db.text(row,"id"));
    scene.put("type",db.text(row,"scene_type"));
    scene.put("title",db.text(row,"title"));
    scene.put("narration",db.text(row,"narration"));
    scene.put("durationInFrames",db.number(row,"duration_in_frames"));
    scenes.add(scene);
   }
  }
  var brief=db.brief(version.getId());
  ObjectNode briefData=db.json().createObjectNode();
  ArrayNode editions=db.json().createArrayNode();
  if(brief!=null){
   briefData.set("brief",db.decode(db.text(brief,"payload"),JsonNode.class));
   briefData.set("evidence",db.json().valueToTree(db.evidence(db.text(brief,"id"))));
  }
  ObjectNode metadata=db.json().createObjectNode()
   .put("title",project.getTitle())
   .put("subject",project.getSubject().name())
   .put("gradeLevel",project.getGradeLevel());
  briefData.set("publicMetadata",metadata);
  var projected=fingerprint.project(scenes,briefData,editions);
  String visualTheme=composition==null?"default":Objects.toString(composition.get("visual_theme"),"default");
  ObjectNode presentation=projected.presentation().deepCopy();
  presentation.put("visualTheme",visualTheme);
  return fingerprint.hash("presentationContract",presentation);
 }
 public void checkRenderFingerprint(ClipProject project,ScriptVersion version,String actor){
  var mode=db.settings(project.getWorkspace().getId());
  if("OFF".equals(mode.mode())||db.brief(version.getId())==null)return;
  String actual=computeScriptFingerprint(project,version);
  String expected=null;
  var rows=db.jdbc().queryForList("select a.payload from stem_assessments a join status_histories h on h.id=a.decision_id where a.version_id=? and a.phase='SCRIPT' order by h.created_at desc, a.id desc limit 1",version.getId());
  if(!rows.isEmpty()){
   JsonNode payload=db.decode(db.text(rows.get(0),"payload"),JsonNode.class);
   expected=payload.path("fingerprint").asText(null);
   if(expected==null||expected.isBlank())expected=payload.path("expectedFingerprint").asText(null);
  }
  if("STRICT".equals(mode.mode())){
   if(expected==null||expected.isBlank()||!actual.equals(expected)){
    throw new ApiException(HttpStatus.CONFLICT,"STEM_FINGERPRINT_STALE","Script fingerprint is stale at render",Map.of("expected",Objects.toString(expected,""),"actual",actual));
   }
  }else if(expected!=null&&!expected.isBlank()&&!actual.equals(expected)){
   if("ADVISORY".equals(mode.mode())){
    db.audit(actor,project.getId(),"STEM_FINGERPRINT_MISMATCH",Map.of("stage","RENDER","expected",expected,"actual",actual));
   }
  }
 }
 public void checkSubmitVideoReview(ClipProject project,ScriptVersion version,String actor){
  var mode=db.settings(project.getWorkspace().getId());
  if("OFF".equals(mode.mode())||db.brief(version.getId())==null)return;
  String currentFp=computeCompositionFingerprint(project,version);
  String expected=getCompositionSnapshot(project.getId(),version.getId());
  if(expected!=null&&!expected.isBlank()&&!currentFp.equals(expected)){
   if("STRICT".equals(mode.mode())){
    throw new ApiException(HttpStatus.CONFLICT,"STEM_FINGERPRINT_STALE","Composition fingerprint does not match snapshot",Map.of("expected",expected,"actual",currentFp));
   }else if("ADVISORY".equals(mode.mode())){
    db.audit(actor,project.getId(),"STEM_FINGERPRINT_MISMATCH",Map.of("stage","SUBMIT_VIDEO_REVIEW","expected",expected,"actual",currentFp));
   }
  }
  saveCompositionSnapshot(project.getId(),version.getId(),currentFp,actor);
 }
 public void checkVideoReviewFingerprint(ClipProject project,ScriptVersion version,String actor,String stage){
  var mode=db.settings(project.getWorkspace().getId());
  if("OFF".equals(mode.mode())||db.brief(version.getId())==null)return;
  String actual=computeCompositionFingerprint(project,version);
  String expected=getCompositionSnapshot(project.getId(),version.getId());
  if("SEND_BACK".equals(stage)){
   if(expected!=null&&!expected.isBlank()&&!actual.equals(expected)){
    db.audit(actor,project.getId(),"STEM_FINGERPRINT_MISMATCH",Map.of("stage","SEND_BACK","expected",expected,"actual",actual));
   }
   return;
  }
  if("STRICT".equals(mode.mode())){
   if(expected==null||expected.isBlank()||!actual.equals(expected)){
    throw new ApiException(HttpStatus.CONFLICT,"STEM_FINGERPRINT_STALE","Composition fingerprint does not match review snapshot",Map.of("expected",Objects.toString(expected,""),"actual",actual));
   }
  }else if(expected!=null&&!expected.isBlank()&&!actual.equals(expected)){
   if("ADVISORY".equals(mode.mode())){
    db.audit(actor,project.getId(),"STEM_FINGERPRINT_MISMATCH",Map.of("stage",stage,"expected",expected,"actual",actual));
   }
  }
 }
 public void checkPublishFingerprint(ClipProject project,ScriptVersion version,String actor){
  var mode=db.settings(project.getWorkspace().getId());
  if("OFF".equals(mode.mode())||db.brief(version.getId())==null)return;
  String actual=computeCompositionFingerprint(project,version);
  String expected=null;
  var rows=db.jdbc().queryForList("select a.payload from stem_assessments a join status_histories h on h.id=a.decision_id where a.version_id=? and a.phase='VIDEO' order by h.created_at desc, a.id desc limit 1",version.getId());
  if(!rows.isEmpty()){
   JsonNode payload=db.decode(db.text(rows.get(0),"payload"),JsonNode.class);
   expected=payload.path("fingerprint").asText(null);
   if(expected==null||expected.isBlank())expected=payload.path("expectedFingerprint").asText(null);
  }
  if("STRICT".equals(mode.mode())){
   if(expected==null||expected.isBlank()||!actual.equals(expected)){
    throw new ApiException(HttpStatus.CONFLICT,"STEM_FINGERPRINT_STALE","Clip/composition fingerprint does not match video approval",Map.of("expected",Objects.toString(expected,""),"actual",actual));
   }
  }else if(expected!=null&&!expected.isBlank()&&!actual.equals(expected)){
   if("ADVISORY".equals(mode.mode())){
    db.audit(actor,project.getId(),"STEM_FINGERPRINT_MISMATCH",Map.of("stage","PUBLISH","expected",expected,"actual",actual));
   }
  }
 }
 public void saveCompositionSnapshot(String project,String version,String fp,String actor){
  db.audit(actor,project,"COMPOSITION_SNAPSHOT",Map.of("fingerprint",fp,"versionId",version));
 }
 public String getCompositionSnapshot(String project,String version){
  var rows=db.jdbc().queryForList("select payload from stem_audit where target=? and event='COMPOSITION_SNAPSHOT' and payload->>'versionId'=? order by created_at desc, id desc limit 1",project,version);
  if(!rows.isEmpty()){
   JsonNode payload=db.decode(db.text(rows.get(0),"payload"),JsonNode.class);
   return payload.path("fingerprint").asText(null);
  }
  return null;
 }
 public StemDtos.ImportResult importCatalog(String actor, com.fasterxml.jackson.databind.JsonNode body){
  if(!access.isAdmin(actor))throw ApiException.forbidden("Only System Admin can import STEM catalog");
  if(body==null||!body.isObject())throw StemValidator.invalid("request","Expected JSON object");
  validator.limits(body,0);
  String idempotencyKey=body.path("idempotencyKey").asText(null);
  validator.idem(idempotencyKey);
  if(body.hasNonNull("schemaVersion"))validator.schema(body.path("schemaVersion").asText());
  String packName=body.hasNonNull("pack")?body.path("pack").asText():null;
  com.fasterxml.jackson.databind.JsonNode itemsNode=body.path("items");
  if((itemsNode.isMissingNode()||itemsNode.isNull()||itemsNode.isEmpty())&&packName!=null){
   String resourcePath="/stem/demo-pack/"+packName+(packName.endsWith(".json")?"":".json");
   try(var is=getClass().getResourceAsStream(resourcePath)){
    if(is==null)throw StemValidator.invalid("pack","Unknown demo pack: "+packName);
    com.fasterxml.jackson.databind.JsonNode packNode=db.json().readTree(is);
    validator.limits(packNode,0);
    if(packNode.hasNonNull("schemaVersion"))validator.schema(packNode.path("schemaVersion").asText());
    itemsNode=packNode.path("items");
   }catch(ApiException e){throw e;}catch(Exception e){throw StemValidator.invalid("pack","Malformed demo pack: "+e.getMessage());}
  }
  if(itemsNode==null||!itemsNode.isArray())throw StemValidator.invalid("items","Expected items array");
  List<com.fasterxml.jackson.databind.JsonNode> items=new ArrayList<>();
  itemsNode.forEach(items::add);
  List<String> order=List.of("SOURCE_EDITION","PROFILE","OUTCOME","RUBRIC","POLICY","TOPIC","TOPIC_VERSION");
  items.sort(Comparator.comparingInt(item->{
   int idx=order.indexOf(item.path("kind").asText(""));
   return idx>=0?idx:999;
  }));
  int created=0,skipped=0;
  for(com.fasterxml.jackson.databind.JsonNode item:items){
   if(!item.isObject())throw StemValidator.invalid("items","Expected item object");
   String kindStr=item.path("kind").asText(null);
   if(kindStr==null)throw StemValidator.invalid("item.kind","Kind is required");
   StemModels.Kind kind;
   try{kind=StemModels.Kind.valueOf(kindStr);}catch(Exception e){throw StemValidator.invalid("item.kind","Invalid catalog kind: "+kindStr);}
   String logicalKey=item.hasNonNull("logicalKey")?item.path("logicalKey").asText():item.path("logical_key").asText(null);
   validator.key(logicalKey,"item.logicalKey");
   int versionNo=item.hasNonNull("versionNo")?item.path("versionNo").asInt(0):item.path("version_no").asInt(0);
   if(versionNo<1)throw StemValidator.invalid("item.versionNo","Expected positive versionNo");
   String id=item.path("id").asText(null);
   if(id!=null)validator.key(id,"item.id");
   String parentId=item.hasNonNull("parentId")?item.path("parentId").asText():item.path("parent_id").asText(null);
   String sourceId=item.hasNonNull("sourceId")?item.path("sourceId").asText():item.path("source_id").asText(null);
   if(item.hasNonNull("status")&&!"DRAFT".equals(item.path("status").asText()))throw StemValidator.invalid("item.status","Only DRAFT status is supported");
   com.fasterxml.jackson.databind.JsonNode data=item.path("data");
   if(data==null||!data.isObject())throw StemValidator.invalid("item.data","Expected object data");
   if(data.hasNonNull("verificationStatus")&&!"UNVERIFIED".equals(data.path("verificationStatus").asText()))throw StemValidator.invalid("item.data.verificationStatus","Only UNVERIFIED status is supported");
   var existingList=db.jdbc().queryForList("select id, kind, logical_key, version_no, parent_id, source_id, status, data from stem_catalog where kind=? and logical_key=? and version_no=?",kind.name(),logicalKey,versionNo);
   if(!existingList.isEmpty()){
    var existing=existingList.get(0);
    String exId=db.text(existing,"id");
    String exParent=db.text(existing,"parent_id");
    String exSource=db.text(existing,"source_id");
    String exStatus=db.text(existing,"status");
    com.fasterxml.jackson.databind.JsonNode exData=db.decode(db.text(existing,"data"),com.fasterxml.jackson.databind.JsonNode.class);
    boolean idMatch=(id==null||Objects.equals(id,exId));
    boolean parentMatch=Objects.equals(parentId,exParent);
    boolean sourceMatch=Objects.equals(sourceId,exSource);
    boolean statusMatch="DRAFT".equals(exStatus);
    boolean dataMatch=Objects.equals(exData,data);
    if(idMatch&&parentMatch&&sourceMatch&&statusMatch&&dataMatch){skipped++;continue;}
    else throw ApiException.conflict("Catalog item conflicts with existing record: "+kind+" "+logicalKey+" v"+versionNo);
   }
   if(parentId!=null){
    int cnt=db.jdbc().queryForObject("select count(*) from stem_catalog where id=?",Integer.class,parentId);
    if(cnt!=1)throw StemValidator.invalid("item.parentId","Parent catalog item does not exist: "+parentId);
   }
   if(sourceId!=null){
    int cnt=db.jdbc().queryForObject("select count(*) from stem_catalog where id=? and kind='SOURCE_EDITION'",Integer.class,sourceId);
    if(cnt!=1)throw StemValidator.invalid("item.sourceId","Source edition catalog item does not exist: "+sourceId);
   }
   String insertId=(id!=null&&!id.isBlank())?id:UUID.randomUUID().toString();
   db.jdbc().update("insert into stem_catalog(id,kind,logical_key,version_no,revision,parent_id,source_id,status,data) values(?,?,?,?,0,?,?,'DRAFT',?::jsonb)",insertId,kind.name(),logicalKey,versionNo,parentId,sourceId,db.encode(data));
   created++;
  }
  db.audit(actor,"stem_catalog","CATALOG_IMPORT",Map.of("pack",packName!=null?packName:"custom","idempotencyKey",idempotencyKey,"created",created,"skipped",skipped));
  return new StemDtos.ImportResult(created,skipped);
 }
}