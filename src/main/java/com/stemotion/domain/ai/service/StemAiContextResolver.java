package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.domain.stem.*;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@ConditionalOnProperty(name="app.stem.enabled",havingValue="true")
public class StemAiContextResolver {
    public record Target(String scriptVersionId,Integer expectedScriptRevision,Integer expectedBriefRevision,Integer expectedModeRevision){}
    public record Resolved(Target target,JsonNode context,String digest,String topic,String subject,String grade,String text){}
    private final StemRepository db;private final StemService stem;private final StemValidator validator;private final WorkspaceAccess access;private final ScriptVersionRepository versions;
    public StemAiContextResolver(StemRepository db,StemService stem,StemValidator validator,WorkspaceAccess access,ScriptVersionRepository versions){this.db=db;this.stem=stem;this.validator=validator;this.access=access;this.versions=versions;}
    @Transactional(readOnly=true)
    public Resolved resolve(String project,String actor,Target target){
        if(target==null)throw StemValidator.invalid("target","Grounding target is required");
        validator.key(target.scriptVersionId(),"scriptVersionId");validator.rev(target.expectedScriptRevision(),"expectedScriptRevision");validator.rev(target.expectedBriefRevision(),"expectedBriefRevision");validator.rev(target.expectedModeRevision(),"expectedModeRevision");
        access.requireProject(project,actor,false);
        var version=versions.findById(target.scriptVersionId()).orElseThrow(()->ApiException.notFound("ScriptVersion",target.scriptVersionId()));
        var p=version.getProject();if(!p.getId().equals(project))throw ApiException.forbidden("Version outside project");
        access.requireActiveStemSeat(p.getWorkspace().getId(),actor,WorkspaceRole.WRITER);
        if(Boolean.TRUE.equals(p.getArchived())||!Boolean.TRUE.equals(version.getIsCurrent())||!Set.of(ScriptStatus.DRAFT,ScriptStatus.CHANGE_REQUESTED).contains(version.getStatus()))throw ApiException.conflict("Grounding requires an editable current draft");
        var view=stem.get(project,version.getId(),actor);
        if(view.briefId()==null)throw StemValidator.invalid("briefId","Create a draft STEM brief before grounded AI");
        if(!target.expectedScriptRevision().equals(version.getDraftRevision())||target.expectedBriefRevision()!=view.revision()||target.expectedModeRevision()!=view.modeRevision())throw stale();
        var root=db.json().createObjectNode().put("schemaVersion","stem-ai-context.v1").put("verificationStatus","UNVERIFIED");
        root.put("projectId",project);root.set("target",db.json().valueToTree(target));
        root.set("brief",db.json().valueToTree(view.brief()));root.set("evidence",db.json().valueToTree(view.evidence()));root.set("findings",db.json().valueToTree(view.findings()));
        var editions=root.putArray("editions");var refs=new TreeSet<String>();var seen=new HashSet<String>();
        var brief=view.brief();if(brief.policyVersionId()!=null)refs.add(brief.policyVersionId());if(brief.topicVersionId()!=null)refs.add(brief.topicVersionId());refs.addAll(brief.outcomeIds());
        while(!refs.isEmpty()){
            String id=refs.pollFirst();if(!seen.add(id))continue;if(seen.size()>200)throw StemValidator.invalid("editions","Maximum catalog closure200");
            var rows=db.jdbc().queryForList("select * from stem_catalog where id=?",id);if(rows.size()!=1)throw StemValidator.invalid("catalog","Missing referenced edition");var row=rows.get(0);
            var e=db.json().createObjectNode().put("id",id).put("kind",db.text(row,"kind")).put("version",db.number(row,"version_no")).put("status",db.text(row,"status")).put("verificationStatus","UNVERIFIED");
            var data=db.decode(db.text(row,"data"),JsonNode.class);e.set("data",data);editions.add(e);
            if("OUTCOME".equals(db.text(row,"kind"))&&(!Objects.equals(brief.gradeKey(),data.path("gradeKey").asText(null))||!brief.subjectKeys().contains(data.path("subjectKey").asText(""))))throw StemValidator.invalid("outcomeIds","Outcome outside grade/subject");
            for(String key:List.of("parent_id","source_id"))if(row.get(key)!=null)refs.add(db.text(row,key));
        }
        String grade=brief.gradeKey()==null?"UNMAPPED":brief.gradeKey();
        root.put("profile",grade.matches("[1-5]")?"PRIMARY_DRAFT":"SECONDARY_DRAFT");
        // This profile selects vocabulary only. It never maps or verifies the curriculum.
        var scenes=root.putArray("scenes");StringBuilder text=new StringBuilder();
        for(var row:db.jdbc().queryForList("select id,scene_type,title,narration,props from scenes where version_id=? order by order_no",version.getId())){
            ObjectNode scene=db.json().createObjectNode().put("id",db.text(row,"id")).put("type",db.text(row,"scene_type")).put("title",db.text(row,"title")).put("narration",db.text(row,"narration"));scene.set("props",db.decode(db.text(row,"props"),JsonNode.class));scenes.add(scene);text.append(db.text(row,"title")).append('\n').append(db.text(row,"narration")).append('\n');
        }
        if(text.length()>60000)throw StemValidator.invalid("scriptText","Grounded script exceeds60000");validator.limits(root,0);
        return new Resolved(target,root,digest(root),p.getTitle(),p.getSubject().name(),grade,text.toString());
    }
    private String digest(JsonNode value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static ApiException stale(){return new ApiException(org.springframework.http.HttpStatus.CONFLICT,"STEM_STALE_TARGET","Grounded target changed; reload and request a new suggestion");}
}
