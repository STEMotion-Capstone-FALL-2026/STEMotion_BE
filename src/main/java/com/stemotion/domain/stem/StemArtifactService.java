package com.stemotion.domain.stem;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.production.entity.*;
import com.stemotion.domain.production.dto.ProductionDtos.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Isolated, additive artifact lineage. All snapshots/proofs are append-only. */
@Service
@ConditionalOnProperty(name="app.stem.artifact.enabled",havingValue="true")
@Transactional
public class StemArtifactService {
    private final StemRepository db;
    private final WorkspaceAccess access;
    private final StemFingerprint fingerprint;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    public StemArtifactService(StemRepository db,WorkspaceAccess access){this.db=db;this.access=access;this.fingerprint=new StemFingerprint(db.json());}
    public static ApiException conflict(String reason){return new ApiException(HttpStatus.CONFLICT,"STEM_STALE_TARGET","Artifact target changed or lacks required acknowledgement",Map.of("reason",reason));}
    private ObjectNode object(){return db.json().createObjectNode();}
    private String text(Map<String,Object> r,String k){return db.text(r,k);}
    private JsonNode tree(Object value){return value==null?NullNode.instance:fingerprint.parse(value.toString());}
    private Map<String,Object> one(String sql,Object...args){return db.jdbc().queryForList(sql,args).stream().findFirst().orElse(null);}
    public record Capture(String project,String version,String workspace,String composition,Integer revision,JsonNode payload,JsonNode scenes,StemFingerprint.Result hashes) {}

    /** Same leading lock order as P3: script version -> workspace -> composition -> project. */
    public void lock(ClipProject project){
        var rows=db.jdbc().queryForList("select id from script_versions where project_id=? and is_current=true for update",project.getId());
        if(rows.size()!=1)throw conflict("REVIEW_TARGET_CHANGED");
        db.jdbc().queryForList("select id from workspaces where id=? for update",project.getWorkspace().getId());
        db.jdbc().queryForList("select id from compositions where script_version_id=? for update",text(rows.get(0),"id"));
        db.jdbc().queryForList("select id from clip_projects where id=? for update",project.getId());
        if(em!=null)em.refresh(project);
    }
    public Capture capture(ClipProject project){
        Map<String,Object> version=one("select * from script_versions where project_id=? and is_current=true",project.getId());
        if(version==null)throw conflict("REVIEW_TARGET_CHANGED");String vid=text(version,"id");
        Map<String,Object> composition=one("select * from compositions where script_version_id=?",vid);
        ArrayNode scenes=db.json().createArrayNode();
        if(composition!=null)scenes=(ArrayNode)tree(composition.get("scenes"));
        else for(var row:db.jdbc().queryForList("select * from scenes where version_id=? order by order_no",vid)){
            ObjectNode scene=(ObjectNode)tree(row.get("props"));scene.put("id",text(row,"id"));scene.put("type",text(row,"scene_type"));
            scene.put("title",text(row,"title"));scene.put("narration",text(row,"narration"));scene.put("durationInFrames",((Number)row.get("duration_in_frames")).intValue());scenes.add(scene);
        }
        var brief=db.brief(vid);ObjectNode briefData=object();ArrayNode editions=db.json().createArrayNode();
        if(brief!=null){briefData.set("brief",tree(brief.get("payload")));briefData.set("evidence",db.json().valueToTree(db.evidence(text(brief,"id"))));
            Set<String> refs=new TreeSet<>();JsonNode b=briefData.path("brief");
            for(String key:List.of("policyVersionId","topicVersionId"))if(b.hasNonNull(key))refs.add(b.path(key).asText());
            b.path("outcomeIds").forEach(n->refs.add(n.asText()));Set<String> seen=new HashSet<>();
            while(!refs.isEmpty()){
                String id=refs.iterator().next();refs.remove(id);if(!seen.add(id))continue;
                if(seen.size()>200)throw StemValidator.invalid("editions","Catalog closure limit");
                Map<String,Object> row=one("select * from stem_catalog where id=?",id);
                if(row==null)throw conflict("REVIEW_TARGET_CHANGED");
                ObjectNode edition=object().put("id",id).put("kind",text(row,"kind")).put("version",((Number)row.get("version_no")).intValue());
                edition.set("data",tree(row.get("data")));edition.put("digest",StemFingerprint.sha(fingerprint.canonical(tree(row.get("data")))));editions.add(edition);
                for(String key:List.of("parent_id","source_id"))if(row.get(key)!=null)refs.add(text(row,key));
            }
        }
        ObjectNode metadata=object().put("title",project.getTitle()).put("subject",project.getSubject().name()).put("gradeLevel",project.getGradeLevel());
        briefData.set("publicMetadata",metadata);
        var projected=fingerprint.project(scenes,briefData,editions);
        String visualTheme=composition==null?"default":Objects.toString(composition.get("visual_theme"),"default");
        ObjectNode presentation=projected.presentation().deepCopy();presentation.put("visualTheme",visualTheme);
        var hashes=new StemFingerprint.Result(projected.semanticHash(),fingerprint.hash("presentationContract",presentation),"default".equals(visualTheme)?projected.classification():"UNKNOWN",projected.semantic(),presentation);
        var mode=db.settings(project.getWorkspace().getId());
        ObjectNode payload=object().put("schemaVersion","stem-snapshot.v1").put("hashVersion",StemFingerprint.VERSION)
            .put("canonicalVersion",StemFingerprint.CANONICAL).put("projectorVersion",StemFingerprint.PROJECTOR).put("classifierVersion",StemFingerprint.CLASSIFIER)
            .put("verificationStatus",brief==null?"LEGACY_UNMAPPED":"UNVERIFIED").put("capturedMode",mode.mode()).put("modeRevision",mode.revision())
            .put("scriptRevision",((Number)version.get("draft_revision")).intValue()).put("briefRevision",brief==null?0:db.number(brief,"revision"));
        payload.set("brief",briefData);payload.set("editions",editions);payload.set("scenes",scenes);
        payload.set("publicMetadata",metadata.deepCopy().put("sceneCount",scenes.size()).put("totalDurationSeconds",totalSeconds(scenes,project.getFps())).put("ownerName",project.getOwner().getFullName()).put("workspaceName",project.getWorkspace().getName()));
        if(brief!=null)payload.put("briefId",text(brief,"id"));
        return new Capture(project.getId(),vid,project.getWorkspace().getId(),composition==null?null:text(composition,"id"),composition==null?null:db.number(composition,"revision"),payload,scenes,hashes);
    }
    private int totalSeconds(JsonNode scenes,Integer fps){long frames=0;for(JsonNode n:scenes)frames+=n.path("durationInFrames").asInt();return (int)Math.ceil(frames/(double)(fps==null||fps<=0?30:fps));}
    private String insert(String stage,Capture c,String actor,String parent,String approval,String job,String clip,String publication,String decision,String assessment,JsonNode frozen,String inputHash,String assurance,JsonNode extras){
        if(em!=null)em.flush();
        ObjectNode payload=c.payload().deepCopy();if(extras!=null)payload.set("lineage",extras);
        String token=Objects.toString(decision,Objects.toString(job,Objects.toString(clip,Objects.toString(publication,fingerprint.canonical(c.payload())))));
        String dedupe=StemFingerprint.sha(stage+"|"+c.project()+"|"+c.version()+"|"+c.composition()+"|"+c.revision()+"|"+token);
        String existingId=db.jdbc().queryForList("select id from stem_p5_snapshots where dedupe_key=?",String.class,dedupe).stream().findFirst().orElse(null);
        if(existingId!=null)return existingId;
        fingerprint.canonical(payload);if(frozen!=null&&fingerprint.canonical(frozen).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>8388608)throw StemValidator.invalid("inputProps","Frozen input exceeds8MiB");
        String id=db.id();db.jdbc().update("insert into stem_p5_snapshots(id,stage,project_id,version_id,workspace_id,actor_id,composition_id,composition_revision,job_id,clip_id,publication_id,decision_id,assessment_id,parent_id,approval_id,semantic_hash,contract_hash,input_hash,classification,assurance,payload,frozen_input,dedupe_key) values(?,?,?,?,?,?,?::uuid,?,?,?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)",
            id,stage,c.project(),c.version(),c.workspace(),actor,c.composition(),c.revision(),job,clip,publication,decision,assessment,parent,approval,c.hashes().semanticHash(),c.hashes().presentationContractHash(),inputHash,c.hashes().classification(),assurance,db.encode(payload),frozen==null?null:db.encode(frozen),dedupe);
        return id;
    }
    public void submission(ClipProject project,String actor){lock(project);Capture c=capture(project);insert("SUBMISSION",c,actor,null,null,null,null,null,null,null,null,null,"UNKNOWN",null);}
    public record Review(Capture capture,Set<String> decisions){}
    public Review beforeReview(ClipProject project){
        lock(project);return new Review(capture(project),new HashSet<>(db.jdbc().queryForList("select id from status_histories where project_id=?",String.class,project.getId())));
    }
    public String afterReview(ClipProject project,String actor,Review review,Clip clip){
        if(em!=null)em.flush();Capture current=capture(project);if(!same(review.capture(),current))throw conflict("REVIEW_TARGET_CHANGED");
        Set<String> decisions=new HashSet<>(db.jdbc().queryForList("select id from status_histories where project_id=? and event_type='REVIEW_DECISION' and decision='APPROVED' and changed_by=?",String.class,project.getId(),actor));decisions.removeAll(review.decisions());
        if(decisions.size()!=1)throw conflict("REVIEW_TARGET_CHANGED");String decision=decisions.iterator().next();
        var a=one("select id from stem_assessments where decision_id=?",decision);
        if(clip!=null)requireArtifactCurrent(project,clip,false);
        return insert("APPROVAL",current,actor,null,null,clip==null?null:clip.getRenderJob().getId(),clip==null?null:clip.getId(),null,decision,a==null?null:text(a,"id"),null,null,"UNKNOWN",null);
    }
    private boolean same(Capture a,Capture b){return a.version().equals(b.version())&&Objects.equals(a.revision(),b.revision())&&a.hashes().semanticHash().equals(b.hashes().semanticHash())&&a.hashes().presentationContractHash().equals(b.hashes().presentationContractHash())&&a.payload().path("modeRevision").equals(b.payload().path("modeRevision"))&&a.payload().path("briefRevision").equals(b.payload().path("briefRevision"));}
    private String inputHash(RenderJob job,JsonNode props){
        ObjectNode input=object().put("fps",job.getFps()).put("resolution",job.getResolution().name()).put("ttsVoice",job.getTtsVoice()).put("rendererIdentity","FullSTEMVideo:baseline-9fe67cf");input.set("inputProps",props);
        return fingerprint.hash("renderInput",input);
    }
    public void freeze(RenderJob job,String approval){
        lock(job.getProject());Capture c=capture(job.getProject());
        if(!c.version().equals(job.getScriptVersion().getId())||!Objects.equals(c.revision(),job.getCompositionRevision()))throw conflict("ARTIFACT_BINDING_MISMATCH");
        if(approval!=null){Map<String,Object> a=snapshot(approval);if(a==null||!"APPROVAL".equals(text(a,"stage"))||!matches(a,c))throw conflict("REVIEW_TARGET_CHANGED");}
        JsonNode props=tree(job.getInputProps());String attempt=db.id();
        insert("RENDER_INPUT",c,job.getRequestedBy().getId(),approval,approval,job.getId(),null,null,null,null,props,inputHash(job,props),"UNKNOWN",object().put("attemptToken",attempt).put("rendererVersion","FullSTEMVideo:baseline-9fe67cf"));
    }
    private Map<String,Object> snapshot(String id){return one("select * from stem_p5_snapshots where id=?",id);}
    private Map<String,Object> jobSnapshot(String job){return one("select * from stem_p5_snapshots where stage='RENDER_INPUT' and job_id=?",job);}
    private boolean matches(Map<String,Object> snap,Capture c){
        return text(snap,"version_id").equals(c.version())&&Objects.equals(snap.get("composition_revision"),c.revision())&&text(snap,"semantic_hash").equals(c.hashes().semanticHash())&&text(snap,"contract_hash").equals(c.hashes().presentationContractHash())&&StemFingerprint.VERSION.equals(tree(snap.get("payload")).path("hashVersion").asText())&&StemFingerprint.CANONICAL.equals(tree(snap.get("payload")).path("canonicalVersion").asText())&&StemFingerprint.PROJECTOR.equals(tree(snap.get("payload")).path("projectorVersion").asText())&&StemFingerprint.CLASSIFIER.equals(tree(snap.get("payload")).path("classifierVersion").asText());
    }
    public boolean callback(RenderJob job,RenderCallbackRequest callback){
        lock(job.getProject());db.jdbc().queryForList("select id from render_jobs where id=? for update",job.getId());
        if(em!=null)em.refresh(job);
        Map<String,Object> snap=jobSnapshot(job.getId());JsonNode manifest=callback.artifactManifest();
        if(snap!=null&&!text(snap,"input_hash").equals(inputHash(job,tree(job.getInputProps()))))throw conflict("ARTIFACT_BINDING_MISMATCH");
        if(manifest!=null&&!manifest.isNull()){
            if(!manifest.isObject()||fingerprint.canonical(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536)throw StemValidator.invalid("artifactManifest","Invalid manifest");
            Set<String> allowed=Set.of("schemaVersion","attemptToken","renderInputHash","snapshotId","rendererVersion","artifactSha256","artifactBytes","assurance");
            manifest.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw StemValidator.invalid("artifactManifest."+k,"Unknown field");});
            if(manifest.hasNonNull("assurance")&&!Set.of("BINDING_ONLY","TEST_ONLY_SYNTHETIC").contains(manifest.path("assurance").asText()))throw StemValidator.invalid("artifactManifest.assurance","Unsupported assurance claim");
            if(snap==null)throw conflict("CALLBACK_MANIFEST_MISMATCH");JsonNode expected=tree(snap.get("payload")).path("lineage");
            if(!"stem-render-manifest.v1".equals(manifest.path("schemaVersion").asText())||!expected.path("attemptToken").equals(manifest.path("attemptToken"))||!text(snap,"id").equals(manifest.path("snapshotId").asText())||!text(snap,"input_hash").equals(manifest.path("renderInputHash").asText())||!expected.path("rendererVersion").equals(manifest.path("rendererVersion")))throw conflict("CALLBACK_MANIFEST_MISMATCH");
            if(manifest.hasNonNull("artifactSha256")&&!manifest.path("artifactSha256").asText().matches("[0-9a-f]{64}"))throw StemValidator.invalid("artifactManifest.artifactSha256","Invalid digest");
            if(manifest.hasNonNull("artifactBytes")&&(!manifest.path("artifactBytes").isIntegralNumber()||!manifest.path("artifactBytes").canConvertToLong()||manifest.path("artifactBytes").asLong()<0||manifest.path("artifactBytes").asLong()>9007199254740991L))throw StemValidator.invalid("artifactManifest.artifactBytes","Invalid size");
        }
        if(job.getStatus()==com.stemotion.core.enums.StemEnums.RenderStatus.COMPLETED&&callback.status()==com.stemotion.core.enums.StemEnums.RenderStatus.COMPLETED){
            Map<String,Object> existing=one("select payload from stem_p5_snapshots where stage='CLIP' and job_id=?",job.getId());
            JsonNode old=existing==null?NullNode.instance:tree(existing.get("payload")).path("lineage");
            JsonNode actual=manifest==null?NullNode.instance:manifest;
            if(!Objects.equals(job.getVideoUrl(),callback.videoUrl())||!old.path("manifest").equals(actual))throw conflict("CALLBACK_MANIFEST_MISMATCH");
        }
        Capture c=capture(job.getProject());return c.version().equals(job.getScriptVersion().getId())&&Objects.equals(c.revision(),job.getCompositionRevision())&&(snap==null||matches(snap,c));
    }
    public void completed(RenderJob job,Clip clip,RenderCallbackRequest callback){
        Map<String,Object> render=jobSnapshot(job.getId());Capture c=render==null?capture(job.getProject()):from(render);
        JsonNode manifest=callback.artifactManifest();ObjectNode data=object().put("videoUrl",clip.getVideoUrl());data.set("manifest",manifest==null?NullNode.instance:manifest);
        String assurance=manifest==null||manifest.isNull()?"UNKNOWN":"BINDING_ONLY";
        insert("CLIP",c,null,render==null?null:text(render,"id"),render==null?null:text(render,"approval_id"),job.getId(),clip.getId(),null,null,null,null,render==null?null:text(render,"input_hash"),assurance,data);
    }
    private Capture from(Map<String,Object> row){
        JsonNode p=tree(row.get("payload"));var hashes=new StemFingerprint.Result(text(row,"semantic_hash"),text(row,"contract_hash"),text(row,"classification"),NullNode.instance,NullNode.instance);
        return new Capture(text(row,"project_id"),text(row,"version_id"),text(row,"workspace_id"),text(row,"composition_id"),(Integer)row.get("composition_revision"),p,p.path("scenes"),hashes);
    }
    public boolean requireArtifactCurrent(ClipProject project,Clip clip,boolean requireApproval){
        lock(project);Capture c=capture(project);RenderJob job=clip.getRenderJob();
        if(!c.version().equals(job.getScriptVersion().getId())||!Objects.equals(c.revision(),job.getCompositionRevision())||!Objects.equals(project.getVideoUrl(),clip.getVideoUrl()))throw conflict("ARTIFACT_BINDING_MISMATCH");
        Map<String,Object> render=jobSnapshot(job.getId());Map<String,Object> artifact=one("select * from stem_p5_snapshots where stage='CLIP' and clip_id=?::uuid",clip.getId());
        if(render!=null&&(!matches(render,c)||!text(render,"input_hash").equals(inputHash(job,tree(job.getInputProps())))))throw conflict("ARTIFACT_BINDING_MISMATCH");
        if(artifact!=null&&!Objects.equals(tree(artifact.get("payload")).path("lineage").path("videoUrl").asText(),clip.getVideoUrl()))throw conflict("ARTIFACT_BINDING_MISMATCH");
        if(requireApproval&&render!=null){
            String aid=artifact==null?null:text(artifact,"approval_id");
            Map<String,Object> approval=aid==null?null:snapshot(aid);
            if(approval==null){List<Map<String,Object>> rows=db.jdbc().queryForList("select * from stem_p5_snapshots where stage='APPROVAL' and clip_id=?::uuid",clip.getId());if(rows.size()==1)approval=rows.get(0);}
            if(approval==null||!matches(approval,c))throw conflict("REVIEW_TARGET_CHANGED");
        }
        return render!=null&&artifact!=null&&"BINDING_ONLY".equals(text(artifact,"assurance"));
    }
    public record PublishCapture(Capture capture,Map<String,Object> artifact){}
    public PublishResponse publishReplay(ClipProject project,String actor,PublishRequest request){
        if(request.idempotencyKey()==null)return null;
        new StemValidator(db.json()).idem(request.idempotencyKey());
        db.jdbc().update("delete from stem_receipts where actor_id=? and operation='P5_PUBLISH' and request_key=? and expires_at<=current_timestamp",actor,request.idempotencyKey());
        Map<String,Object> r=one("select * from stem_receipts where actor_id=? and operation='P5_PUBLISH' and request_key=?",actor,request.idempotencyKey());
        if(r==null)return null;
        if(!project.getId().equals(text(r,"target"))||!StemFingerprint.sha(fingerprint.canonical(db.json().valueToTree(request))).equals(text(r,"request_digest")))throw new ApiException(HttpStatus.CONFLICT,"STEM_IDEMPOTENCY_CONFLICT","Publish key reused");
        return db.decode(text(r,"response"),PublishResponse.class);
    }
    public void rememberPublish(ClipProject project,String actor,PublishRequest request,PublishResponse response){
        if(request.idempotencyKey()==null)return;
        db.jdbc().update("insert into stem_receipts(actor_id,operation,target,request_key,request_digest,response,expires_at) values(?,'P5_PUBLISH',?,?,?,?::jsonb,current_timestamp+interval '24 hours')",actor,project.getId(),request.idempotencyKey(),StemFingerprint.sha(fingerprint.canonical(db.json().valueToTree(request))),db.encode(response));
    }
    public String approvalForJob(String job){var row=jobSnapshot(job);return row==null?null:text(row,"approval_id");}
    public void scriptApproved(ClipProject project,String actor,String decision,String assessment){
        insert("APPROVAL",capture(project),actor,null,null,null,null,null,decision,assessment,null,null,"UNKNOWN",object().put("phase","SCRIPT"));
    }
    public void measured(ClipProject project,String actor,Capture before){
        if(em!=null)em.flush();Capture after=capture(project);
        String oldId=insert("SUBMISSION",before,actor,null,null,null,null,null,null,null,null,null,"UNKNOWN",null);
        String newId=insert("SUBMISSION",after,actor,oldId,null,null,null,null,null,null,null,null,"UNKNOWN",null);
        boolean compatible=fingerprint.compatible(before.scenes(),after.scenes(),before.hashes(),after.hashes());
        for(var approval:db.jdbc().queryForList("select * from stem_p5_snapshots where stage='APPROVAL' and version_id=?",before.version())){
            boolean scriptSource="SCRIPT".equals(tree(approval.get("payload")).path("lineage").path("phase").asText())&&text(approval,"semantic_hash").equals(before.hashes().semanticHash())&&text(approval,"contract_hash").equals(before.hashes().presentationContractHash())&&StemFingerprint.VERSION.equals(tree(approval.get("payload")).path("hashVersion").asText());
            if(!matches(approval,before)&&!scriptSource)continue;
            JsonNode proof=object().put("technicalOnly",true).put("pixelAudioVerified",false).put("semanticHash",after.hashes().semanticHash()).put("presentationContractHash",after.hashes().presentationContractHash());
            db.jdbc().update("insert into stem_p5_compatibility(id,approval_id,before_id,after_id,proof_version,result,proof) values(?,?,?,?,?,?,?::jsonb) on conflict(approval_id,before_id,after_id,proof_version) do nothing",db.id(),text(approval,"id"),oldId,newId,"stem-timing-proof.v1",compatible?"COMPATIBLE":"UNKNOWN",db.encode(proof));
        }
    }
    public PublishCapture beforePublish(ClipProject project,Clip clip,PublishRequest request){
        boolean known=requireArtifactCurrent(project,clip,true);
        var expected=request.artifactExpectation();
        var artifact=one("select * from stem_p5_snapshots where stage='CLIP' and clip_id=?::uuid",clip.getId());
        if(expected!=null&&(!clip.getId().equals(expected.clipId())||!clip.getRenderJob().getId().equals(expected.jobId())||artifact==null||!text(artifact,"id").equals(expected.snapshotId())||!Objects.equals(text(artifact,"input_hash"),expected.inputHash())))throw conflict("ARTIFACT_BINDING_MISMATCH");
        if(!known&&!Boolean.TRUE.equals(request.legacyUnverifiedAcknowledged()))throw conflict("LEGACY_ACK_REQUIRED");
        return new PublishCapture(capture(project),artifact);
    }
    public void published(Publication publication,String actor,PublishCapture before){
        Capture c=capture(publication.getProject());if(!same(before.capture(),c))throw conflict("REVIEW_TARGET_CHANGED");
        var a=before.artifact();ObjectNode lineage=object().put("videoUrl",publication.getClip().getVideoUrl()).put("publicationMetadata",publication.getMetadata());
        insert("PUBLICATION",c,actor,a==null?null:text(a,"id"),a==null?null:text(a,"approval_id"),publication.getClip().getRenderJob().getId(),publication.getClip().getId(),publication.getId(),null,null,null,a==null?null:text(a,"input_hash"),a==null?"UNKNOWN":text(a,"assurance"),lineage);
    }
    public JsonNode publicationMetadata(String publication){
        var row=one("select id,payload from stem_p5_snapshots where stage='PUBLICATION' and publication_id=?::uuid",publication);
        if(row==null)return NullNode.instance;
        JsonNode payload=tree(row.get("payload"));ObjectNode metadata=payload.path("publicMetadata").deepCopy();
        return metadata.put("verificationStatus",payload.path("verificationStatus").asText("UNVERIFIED")).put("snapshotId",text(row,"id"));
    }
    public JsonNode frozenScenes(Clip clip){Map<String,Object> row=jobSnapshot(clip.getRenderJob().getId());return row==null?NullNode.instance:tree(row.get("frozen_input")).path("scenes");}
    public Map<String,String> frozenReference(Clip clip){var row=jobSnapshot(clip.getRenderJob().getId());return row==null?Map.of("jobId",clip.getRenderJob().getId(),"assurance","UNKNOWN"):Map.of("jobId",clip.getRenderJob().getId(),"snapshotId",text(row,"id"),"inputHash",text(row,"input_hash"));}
    public Map<String,Object> status(ClipProject project,String actor){
        access.requireProject(project.getId(),actor,false);
        // The controller's read transaction has ended; load this graph in our transaction.
        if(em!=null)project=em.find(ClipProject.class,project.getId());
        Capture c=capture(project);
        var rows=db.jdbc().queryForList("select s.* from stem_p5_snapshots s join clips c on c.id=s.clip_id where s.stage='CLIP' and s.project_id=? and c.video_url=?",project.getId(),project.getVideoUrl());
        Map<String,Object> artifact=rows.size()==1?rows.get(0):null;boolean matches=artifact!=null&&matches(artifact,c);
        boolean known=matches&&"BINDING_ONLY".equals(text(artifact,"assurance"));
        boolean currentProof=db.jdbc().queryForObject("select count(*) from stem_p5_compatibility p join stem_p5_snapshots s on s.id=p.after_id where s.project_id=? and s.version_id=? and s.composition_revision is not distinct from ? and s.semantic_hash=? and s.contract_hash=? and p.result='COMPATIBLE'",Integer.class,c.project(),c.version(),c.revision(),c.hashes().semanticHash(),c.hashes().presentationContractHash())>0;
        ObjectNode target=object().put("projectId",c.project()).put("versionId",c.version()).put("compositionRevision",c.revision());
        if(artifact!=null){target.put("clipId",text(artifact,"clip_id")).put("jobId",text(artifact,"job_id")).put("snapshotId",text(artifact,"id")).put("inputHash",text(artifact,"input_hash"));}
        var mode=db.settings(c.workspace());
        return Map.of("schemaVersion","stem-artifact.v1","target",target,"freshness",matches||currentProof?"CURRENT":"NEEDS_REVIEW","classification",c.hashes().classification(),"integrity",artifact==null?"UNKNOWN":matches?"MATCH":"MISMATCH","publishEligibility",Map.of("allowed",matches&&project.getVideoStatus()==com.stemotion.core.enums.StemEnums.VideoStatus.APPROVED,"route",known?"CURRENT":artifact==null||matches?"LEGACY_UNVERIFIED":"BLOCKED","requiresLegacyAcknowledgement",!known),"snapshotOrigin",artifact==null?Map.of():Map.of("snapshotId",text(artifact,"id"),"capturedMode",tree(artifact.get("payload")).path("capturedMode").asText(),"effectiveMode",mode.mode()),"verificationStatus","UNVERIFIED","reasons",matches?List.of():List.of(artifact==null?"LEGACY_ACK_REQUIRED":"ARTIFACT_BINDING_MISMATCH"));
    }
}
