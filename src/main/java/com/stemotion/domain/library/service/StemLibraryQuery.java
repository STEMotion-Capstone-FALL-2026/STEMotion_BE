package com.stemotion.domain.library.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.web.PageParams;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.domain.library.dto.LibraryDtos.LibraryItem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** P6 read projection. Predicates, count, facets and paging operate on frozen publication rows. */
@Service
@ConditionalOnProperty(name="app.stem.artifact.enabled",havingValue="true")
@Transactional(readOnly=true)
public class StemLibraryQuery {
    private final NamedParameterJdbcTemplate db;
    private final ObjectMapper json;
    public StemLibraryQuery(org.springframework.jdbc.core.JdbcTemplate jdbc,ObjectMapper json){this.db=new NamedParameterJdbcTemplate(jdbc);this.json=json;}
    public record Filters(StemSubject subject,String grade,String educationLevel,String subjectId,String outcomeId,
                          String usageContext,String contextRole,String channel,String verificationStatus,String q) {
        public static Filters simple(StemSubject subject,String grade){return new Filters(subject,grade,null,null,null,null,null,null,null,null);}
        public boolean extended(){return educationLevel!=null||subjectId!=null||outcomeId!=null||usageContext!=null||channel!=null||verificationStatus!=null||q!=null;}
    }
    private static final String BRIEF="s.payload #> '{brief,brief}'";
    private static final String GRADE="case when s.id is null then p.grade_level else s.payload #>> '{publicMetadata,gradeLevel}' end";
    private static final String SUBJECT="case when s.id is null then p.subject::text else s.payload #>> '{publicMetadata,subject}' end";
    private static final String TITLE="case when s.id is null then p.title else s.payload #>> '{publicMetadata,title}' end";
    private static final String STATUS="case when s.id is null then 'LEGACY_UNMAPPED' else coalesce(s.payload->>'verificationStatus','UNVERIFIED') end";
    // A frozen edition can supply a level only for this grade. Never infer it from mutable catalogs or a numeric legacy label.
    private static final String LEVEL="(select case when count(distinct e->'data'->>'educationLevel')=1 then min(e->'data'->>'educationLevel') end from jsonb_array_elements(coalesce(s.payload->'editions','[]'::jsonb)) e where e->'data'->>'educationLevel' in ('PRIMARY','LOWER_SECONDARY','UPPER_SECONDARY') and (e->'data'->>'gradeKey')=("+BRIEF+"->>'gradeKey'))";
    private static final String FROM=" from publications pub join clips clip on clip.id=pub.clip_id join clip_projects p on p.id=pub.project_id join users u on u.id=p.owner_id join workspaces w on w.id=p.workspace_id left join stem_p5_snapshots s on s.publication_id=pub.id and s.stage='PUBLICATION' ";
    private static final String BASE="pub.platform::text='INTERNAL_LIBRARY' and p.video_status::text='PUBLISHED' and (p.archived is null or p.archived=false)";
    private record Predicate(String sql,Map<String,Object> values){}
    private String key(String value,String field){if(value==null||value.isBlank())return null;if(value.length()>128)throw ApiException.badRequest(field+" exceeds128");return value.trim();}
    private String choice(String value,String field,Set<String> allowed){String v=key(value,field);if(v!=null&&!allowed.contains(v))throw ApiException.badRequest("Invalid "+field);return v;}
    private Predicate predicate(Filters f){
        var a=new HashMap<String,Object>();StringBuilder where=new StringBuilder(BASE);
        if(f.subject()!=null){where.append(" and ").append(SUBJECT).append("=:subject");a.put("subject",f.subject().name());}
        String grade=key(f.grade(),"grade");if(grade!=null){where.append(" and lower(").append(GRADE).append(")=lower(:grade)");a.put("grade",grade);}
        String level=choice(f.educationLevel(),"educationLevel",Set.of("PRIMARY","LOWER_SECONDARY","UPPER_SECONDARY"));
        if(level!=null){where.append(" and ").append(LEVEL).append("=:level");a.put("level",level);}
        for(var pair:List.of(new String[]{"subjectId",f.subjectId(),"subjectKeys"},new String[]{"outcomeId",f.outcomeId(),"outcomeIds"},new String[]{"channel",choice(f.channel(),"channel",Set.of("DEPARTMENT_LIBRARY")),"distributionChannels"})){
            String value=key(pair[1],pair[0]);if(value!=null){where.append(" and exists(select 1 from jsonb_array_elements_text(coalesce(").append(BRIEF).append("->'").append(pair[2]).append("','[]'::jsonb)) ref where ref=:").append(pair[0]).append(")");a.put(pair[0],value);}
        }
        String role=choice(f.contextRole(),"contextRole",Set.of("PRIMARY","SECONDARY","ANY"));
        String context=choice(f.usageContext(),"usageContext",Set.of("IN_CLASS_SCREEN","GROUP_PRACTICE"));
        if(context!=null){
            String primary=BRIEF+"#>>'{context,primary,key}'=:context";
            String secondary="exists(select 1 from jsonb_array_elements(coalesce("+BRIEF+"#>'{context,secondary}','[]'::jsonb)) ctx where ctx->>'key'=:context)";
            where.append(" and (").append("SECONDARY".equals(role)?secondary:"ANY".equals(role)?primary+" or "+secondary:primary).append(")");a.put("context",context);
        }
        String status=choice(f.verificationStatus(),"verificationStatus",Set.of("LEGACY_UNMAPPED","UNVERIFIED"));if(status!=null){where.append(" and ").append(STATUS).append("=:verification");a.put("verification",status);}
        if(f.q()!=null&&!f.q().isBlank()){
            if(f.q().length()>200)throw ApiException.badRequest("q exceeds200");
            where.append(" and position(lower(:q) in lower(coalesce(").append(TITLE).append(",'') || ' ' || coalesce(").append(GRADE).append(",'')))>0");a.put("q",f.q().trim());
        }
        return new Predicate(where.toString(),a);
    }
    public List<LibraryItem> find(Filters filters,PageParams page){
        return find(filters,page,null);
    }
    public LibraryItem findOne(String project){return find(Filters.simple(null,null),null,project).stream().findFirst().orElseThrow(()->ApiException.notFound("Library item",project));}
    private List<LibraryItem> find(Filters filters,PageParams page,String project){
        var p=predicate(filters);var args=new HashMap<>(p.values());String sql="select pub.id::text publication_id,p.id project_id,s.id snapshot_id,s.payload::text payload,clip.video_url,pub.metadata,pub.published_at,"+TITLE+" title,"+SUBJECT+" subject,"+GRADE+" grade,"+LEVEL+" level,"+STATUS+" verification,"+
            "case when s.id is null then p.total_duration_seconds else (s.payload#>>'{publicMetadata,totalDurationSeconds}')::integer end duration,"+
            "case when s.id is null then (select count(*) from scenes sc join script_versions sv on sv.id=sc.version_id where sv.project_id=p.id and sv.is_current=true) else (s.payload#>>'{publicMetadata,sceneCount}')::integer end scene_count,"+
            "case when s.id is null then u.full_name else s.payload#>>'{publicMetadata,ownerName}' end owner_name,"+
            "case when s.id is null then w.name else s.payload#>>'{publicMetadata,workspaceName}' end workspace_name"+FROM+" where "+p.sql();
        if(project!=null){sql+=" and p.id=:project";args.put("project",project);}sql+=" order by pub.published_at desc,pub.id desc";
        if(page!=null){sql+=" limit :limit offset :offset";args.put("limit",page.size());args.put("offset",(long)page.page()*page.size());}
        return db.query(sql,args,(rs,n)->{
            JsonNode payload=read(rs.getString("payload"));JsonNode export=read(rs.getString("metadata"));String video=rs.getString("video_url");
            String rawSubject=rs.getString("subject");String embed=export.path("lmsEmbedCode").asText(null);
            return new LibraryItem(rs.getString("project_id"),rs.getString("title"),rawSubject==null?null:StemSubject.valueOf(rawSubject),rs.getString("grade"),(Integer)rs.getObject("duration"),rs.getInt("scene_count"),rs.getString("owner_name"),rs.getString("workspace_name"),video,embed==null?com.stemotion.domain.production.service.VideoEmbed.build(video):embed,export.path("youtubeUrl").asText(null),rs.getTimestamp("published_at").toLocalDateTime(),rs.getString("verification"),rs.getString("snapshot_id"),card(payload,rs.getString("publication_id"),rs.getString("snapshot_id"),rs.getString("level")));
        });
    }
    public long count(Filters filters){var p=predicate(filters);return Objects.requireNonNull(db.queryForObject("select count(*)"+FROM+" where "+p.sql(),p.values(),Long.class));}
    private JsonNode read(String value){if(value==null)return json.createObjectNode();try{return json.readTree(value);}catch(Exception e){throw ApiException.conflict("Unreadable frozen library metadata");}}
    public JsonNode card(JsonNode payload,String publication,String snapshot,String level){
        var card=json.createObjectNode().put("schemaVersion","stem-learning-card.v1").put("publicationId",publication);
        if(snapshot==null){card.put("verificationStatus","LEGACY_UNMAPPED");return card;}
        card.put("snapshotId",snapshot).put("verificationStatus",payload.path("verificationStatus").asText("UNVERIFIED"));
        if(level!=null)card.put("educationLevel",level);
        JsonNode brief=payload.path("brief").path("brief");
        for(String key:List.of("gradeKey","subjectKeys","outcomeIds","context","distributionChannels","objectives","studentTasks","scopeOfClip","safetyNotes"))if(brief.has(key))card.set(key,brief.get(key).deepCopy());
        var editions=card.putArray("sources");for(JsonNode e:payload.path("editions")){
            var item=json.createObjectNode();for(String key:List.of("id","kind","version","digest"))if(e.has(key))item.set(key,e.get(key));
            for(String key:List.of("title","citation","locator","url","documentCode"))if(e.path("data").has(key))item.set(key,e.path("data").get(key));editions.add(item);
        }
        card.put("capturedMode",payload.path("capturedMode").asText("OFF")).put("expertVerified",false);
        return card;
    }
    public Map<String,List<String>> facets(Filters filters){
        var p=predicate(filters);var result=new LinkedHashMap<String,List<String>>();
        Map<String,String> scalars=Map.of("educationLevels",LEVEL,"subjects",SUBJECT,"gradeLevels",GRADE,"verificationStatuses",STATUS);
        scalars.forEach((name,expression)->result.put(name,db.queryForList("select distinct "+expression+" value"+FROM+" where "+p.sql()+" order by value",p.values(),String.class).stream().filter(Objects::nonNull).toList()));
        Map<String,String> arrays=Map.of("subjectIds","subjectKeys","outcomeIds","outcomeIds","channels","distributionChannels");
        arrays.forEach((name,key)->result.put(name,db.queryForList("select distinct ref value"+FROM+" cross join lateral jsonb_array_elements_text(coalesce("+BRIEF+"->'"+key+"','[]'::jsonb)) ref where "+p.sql()+" order by value",p.values(),String.class)));
        result.put("usageContexts",db.queryForList("select distinct ctx->>'key' value"+FROM+" cross join lateral jsonb_array_elements(coalesce("+BRIEF+"#>'{context,secondary}','[]'::jsonb) || jsonb_build_array("+BRIEF+"#>'{context,primary}')) ctx where "+p.sql()+" and ctx->>'key' is not null order by value",p.values(),String.class));
        return result;
    }
}
