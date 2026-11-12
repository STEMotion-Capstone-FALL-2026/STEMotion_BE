package com.stemotion.e2e;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stemotion.StemotionApplication;
import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.domain.auth.entity.*;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.entity.*;
import com.stemotion.domain.script.repository.*;
import com.stemotion.domain.workspace.entity.*;
import com.stemotion.domain.workspace.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Full Spring/JWT/HTTP/owned PG; only external render/provider integrations are fake. */
class StemArtifactP5Test {
    static final ObjectMapper JSON=new ObjectMapper();static final HttpClient HTTP=HttpClient.newHttpClient();
    static ConfigurableApplicationContext context;static JdbcTemplate db;static String base,writer,reviewer,producer,admin,writerId,reviewerId,producerId;
    String project,version,workspace;
    @BeforeAll static void boot() throws Exception {
        System.setProperty("spring.devtools.restart.enabled","false");
        context=new SpringApplication(StemotionApplication.class,E2eServer.Config.class).run(E2eServer.launchArguments(
            "--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=stem-p5-test","--spring.dotenv.enabled=false",
            "--spring.flyway.locations=classpath:db/migration,classpath:db/auth-v2-e2e,classpath:db/stem-p3,classpath:db/stem-p5",
            "--app.stem.enabled=true","--app.stem.artifact.enabled=true","--app.notification-email.enabled=false",
            "--app.integration.render.max-attempts=3",
            "--app.integration.render.callback-url=http://127.0.0.1:9/unused-p5-test-callback",
            "--logging.level.root=WARN","--logging.level.com.stemotion=WARN","--logging.level.org.hibernate.SQL=WARN",
            "--logging.level.io.zonky.test.db.postgres.embedded.EmbeddedPostgres=INFO"));
        base="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort()+"/api/v1";db=context.getBean(JdbcTemplate.class);
        assertThat(db.queryForObject("select count(*) from flyway_schema_history where success",Integer.class)).isEqualTo(28);
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        List<String> ids=tx.execute(s->{List<String> result=new ArrayList<>();for(String role:List.of("writer","reviewer","producer"))result.add(context.getBean(UserRepository.class).saveAndFlush(User.builder().fullName("TEST_ONLY "+role).username("p5-"+role).passwordHash(context.getBean(PasswordEncoder.class).encode("P5-fixture-only-123")).systemRole(SystemRole.USER).status(User.UserStatus.ACTIVE).mustChangePassword(false).build()).getId());return result;});
        writerId=ids.get(0);reviewerId=ids.get(1);producerId=ids.get(2);
        writer=login("p5-writer","P5-fixture-only-123");reviewer=login("p5-reviewer","P5-fixture-only-123");producer=login("p5-producer","P5-fixture-only-123");admin=login("e2e-admin","E2e-fixture-only-123");
    }
    @AfterAll static void close(){if(context!=null)context.close();}
    @BeforeEach void seed(){
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(s->{
            var users=context.getBean(UserRepository.class);var owner=users.findById(writerId).orElseThrow();
            var ws=context.getBean(WorkspaceRepository.class).saveAndFlush(Workspace.builder().name("TEST_ONLY P5 "+UUID.randomUUID()).createdBy(owner).build());workspace=ws.getId();
            var members=context.getBean(WorkspaceMemberRepository.class);
            for(var pair:List.of(Map.entry(writerId,WorkspaceRole.WRITER),Map.entry(reviewerId,WorkspaceRole.REVIEWER),Map.entry(producerId,WorkspaceRole.PRODUCER)))members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(users.findById(pair.getKey()).orElseThrow()).role(pair.getValue()).build());
            var p=context.getBean(ClipProjectRepository.class).saveAndFlush(ClipProject.builder().title("TEST_ONLY P5 unverified").subject(StemSubject.Math).gradeLevel("4").fps(30).totalDurationSeconds(20).owner(owner).workspace(ws).scriptStatus(ScriptStatus.APPROVED).videoStatus(VideoStatus.NOT_RENDERED).build());project=p.getId();
            var v=context.getBean(ScriptVersionRepository.class).saveAndFlush(ScriptVersion.builder().project(p).title("TEST_ONLY").versionNo(1).createdBy(owner).status(ScriptStatus.APPROVED).build());version=v.getId();
            var scenes=context.getBean(SceneRepository.class);
            scenes.saveAndFlush(Scene.builder().scriptVersion(v).sceneType(SceneType.TITLE_HERO).title("TEST_ONLY question").narration("TEST_ONLY").durationInFrames(300).props("{\"subtitle\":\"TEST_ONLY\",\"subject\":\"Math\",\"gradeLevel\":\"4\",\"badgeText\":\"TEST_ONLY\"}").orderNo(1).build());
            scenes.saveAndFlush(Scene.builder().scriptVersion(v).sceneType(SceneType.OUTRO).title("TEST_ONLY conclusion").narration("TEST_ONLY").durationInFrames(300).props("{\"summaryPoints\":[\"TEST_ONLY\"],\"nextLessonSuggestion\":\"TEST_ONLY\",\"instructorName\":\"TEST_ONLY\"}").orderNo(2).build());
            return null;
        });
    }
    String path(String action){return "/scripts/"+project+"/"+action;}
    static JsonNode node(HttpResponse<String> r)throws Exception{JsonNode n=JSON.readTree(r.body());return n.has("data")?n.get("data"):n;}
    static String login(String u,String p)throws Exception{var r=call("POST","/auth/login",Map.of("username",u,"password",p),null);assertThat(r.statusCode()).isEqualTo(200);return node(r).path("accessToken").asText();}
    static HttpResponse<String> call(String method,String path,Object body,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json");if(token!=null)b.header("Authorization","Bearer "+token);
        return HTTP.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> callback(String job,JsonNode manifest,String url)throws Exception{
        ObjectNode body=JSON.createObjectNode().put("jobId",job).put("status","COMPLETED").put("videoUrl",url);if(manifest!=null)body.set("artifactManifest",manifest);
        var req=HttpRequest.newBuilder(URI.create(base+"/render/callback")).header("Content-Type","application/json").header("X-Render-Api-Key","e2e-worker-key").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();return HTTP.send(req,HttpResponse.BodyHandlers.ofString());
    }
    JsonNode manifest(String job)throws Exception{
        var row=db.queryForMap("select id,input_hash,payload from stem_p5_snapshots where stage='RENDER_INPUT' and job_id=?",job);JsonNode lineage=JSON.readTree(row.get("payload").toString()).path("lineage");
        return JSON.createObjectNode().put("schemaVersion","stem-render-manifest.v1").put("attemptToken",lineage.path("attemptToken").asText()).put("renderInputHash",row.get("input_hash").toString()).put("snapshotId",row.get("id").toString()).put("rendererVersion",lineage.path("rendererVersion").asText()).put("assurance","BINDING_ONLY");
    }
    String rendered(boolean withManifest)throws Exception{
        var start=call("POST","/render/projects/"+project+"/start",Map.of(),producer);assertThat(start.statusCode()).as(start.body()).isEqualTo(202);String job=node(start).path("jobId").asText();
        var done=callback(job,withManifest?manifest(job):null,"https://example.invalid/"+project+".mp4");assertThat(done.statusCode()).as(done.body()).isEqualTo(200);return job;
    }
    String compositionApproved()throws Exception{
        var submit=call("POST",path("submit-video-review"),Map.of(),producer);assertThat(submit.statusCode()).as(submit.body()).isEqualTo(200);
        var approve=call("POST",path("approve-video"),Map.of("note","TEST_ONLY workflow, not expert approval"),reviewer);assertThat(approve.statusCode()).as(approve.body()).isEqualTo(204);
        return db.queryForObject("select id from render_jobs where project_id=?",String.class,project);
    }
    ObjectNode publishBody(boolean ack){return JSON.createObjectNode().put("title","TEST_ONLY snapshot").put("legacyUnverifiedAcknowledged",ack).put("idempotencyKey","p5-"+UUID.randomUUID()).set("targets",JSON.createArrayNode().add("INTERNAL_LIBRARY"));}
    int count(String stage){return db.queryForObject("select count(*) from stem_p5_snapshots where project_id=? and stage=?",Integer.class,project,stage);}

    @Test void B02CompositionReviewFrozenCallbackPublishAndSnapshotConsumption()throws Exception{
        String job=compositionApproved();assertThat(count("SUBMISSION")).isEqualTo(1);assertThat(count("APPROVAL")).isEqualTo(1);assertThat(count("RENDER_INPUT")).isEqualTo(1);
        var done=callback(job,manifest(job),"https://example.invalid/"+project+".mp4");assertThat(done.statusCode()).as(done.body()).isEqualTo(200);assertThat(count("CLIP")).isEqualTo(1);
        var body=publishBody(false);var pub=call("POST",path("publish"),body,producer);assertThat(pub.statusCode()).as(pub.body()).isEqualTo(201);assertThat(count("PUBLICATION")).isEqualTo(1);
        assertThat(call("POST",path("publish"),body,producer).statusCode()).isEqualTo(201);assertThat(count("PUBLICATION")).isEqualTo(1);
        db.update("update clip_projects set title='TEST_ONLY mutable new title',grade_level='11' where id=?",project);
        var detail=call("GET","/library/clips/"+project,null,writer);assertThat(detail.statusCode()).isEqualTo(200);assertThat(node(detail).path("title").asText()).isEqualTo("TEST_ONLY P5 unverified");assertThat(node(detail).path("gradeLevel").asText()).isEqualTo("4");assertThat(node(detail).path("verificationStatus").asText()).isEqualTo("LEGACY_UNMAPPED");assertThat(node(detail).path("snapshotId").asText()).isNotBlank();
        var filters=call("GET","/library/filters",null,writer);assertThat(node(filters).path("gradeLevels").toString()).contains("4");
    }
    @Test void B01RenderedClipReviewDoesNotEnqueueReplacement()throws Exception{
        rendered(true);var approve=call("POST",path("approve-video"),Map.of("note","TEST_ONLY"),reviewer);assertThat(approve.statusCode()).as(approve.body()).isEqualTo(204);
        assertThat(db.queryForObject("select count(*) from render_jobs where project_id=?",Integer.class,project)).isEqualTo(1);
        assertThat(call("POST",path("publish"),publishBody(false),producer).statusCode()).isEqualTo(201);
    }
    @Test void B03WrongManifestHard409WritesNoArtifactEvenWithLegacyAck()throws Exception{
        String job=compositionApproved();ObjectNode bad=(ObjectNode)manifest(job);bad.put("renderInputHash","0".repeat(64));
        var r=callback(job,bad,"https://example.invalid/bad.mp4");assertThat(r.statusCode()).isEqualTo(409);assertThat(count("CLIP")).isZero();
        assertThat(db.queryForObject("select video_url from clip_projects where id=?",String.class,project)).isNull();
    }
    @Test void B03DuplicateSameCompletionNoopContradictoryCompletion409()throws Exception{
        String job=compositionApproved();JsonNode m=manifest(job);String url="https://example.invalid/"+project+".mp4";
        assertThat(callback(job,m,url).statusCode()).isEqualTo(200);assertThat(callback(job,m,url).statusCode()).isEqualTo(200);assertThat(count("CLIP")).isEqualTo(1);assertThat(callback(job,m,url+"?different=1").statusCode()).isEqualTo(409);
    }
    @Test void P02MissingManifestRequiresExplicitUnverifiedAndKeepsHistory()throws Exception{
        String job=compositionApproved();assertThat(callback(job,null,"https://example.invalid/"+project+".mp4").statusCode()).isEqualTo(200);
        var no=call("POST",path("publish"),publishBody(false),producer);assertThat(no.statusCode()).isEqualTo(409);assertThat(count("PUBLICATION")).isZero();assertThat(no.body()).contains("LEGACY_ACK_REQUIRED");
        assertThat(call("POST",path("publish"),publishBody(true),producer).statusCode()).isEqualTo(201);
        assertThat(db.queryForObject("select count(*) from status_histories where project_id=? and decision='APPROVED'",Integer.class,project)).isEqualTo(1);
        assertThat(db.queryForObject("select payload->>'verificationStatus' from stem_p5_snapshots where stage='PUBLICATION' and project_id=?",String.class,project)).isEqualTo("LEGACY_UNMAPPED");
    }
    @Test void P01ChangedCompositionBlocksPublishEvenExplicitUnverified()throws Exception{
        String job=compositionApproved();callback(job,manifest(job),"https://example.invalid/"+project+".mp4");
        db.update("update compositions set revision=revision+1 where project_id=?",project);
        assertThat(call("POST",path("publish"),publishBody(true),producer).statusCode()).isEqualTo(409);assertThat(count("PUBLICATION")).isZero();
    }
    @Test void P03YoutubeUnavailableAtomicNoWrite()throws Exception{
        String job=compositionApproved();callback(job,manifest(job),"https://example.invalid/"+project+".mp4");ObjectNode body=publishBody(false);body.set("targets",JSON.createArrayNode().add("YOUTUBE"));
        var r=call("POST",path("publish"),body,producer);assertThat(r.statusCode()).isEqualTo(503);assertThat(r.body()).contains("PUBLISH_TARGET_UNAVAILABLE");assertThat(count("PUBLICATION")).isZero();
    }
    @Test void G05ExistingMediaFailHardBlocksOffMode()throws Exception{
        db.update("update scenes set duration_in_frames=1 where version_id=?",version);String job=compositionApproved();callback(job,manifest(job),"https://example.invalid/"+project+".mp4");
        var qa=call("GET",path("qa-report"),null,producer);assertThat(qa.statusCode()).isEqualTo(200);assertThat(node(qa).path("passed").asBoolean()).isFalse();
        assertThat(call("POST",path("publish"),publishBody(true),producer).statusCode()).isEqualTo(400);assertThat(count("PUBLICATION")).isZero();
    }
    @Test void B04SendBackDoesNotRequireStemPass()throws Exception{
        assertThat(call("POST",path("submit-video-review"),Map.of(),producer).statusCode()).isEqualTo(200);
        assertThat(call("POST",path("send-back"),Map.of("note","TEST_ONLY needs work"),reviewer).statusCode()).isEqualTo(204);
        assertThat(count("APPROVAL")).isZero();assertThat(count("SUBMISSION")).isEqualTo(1);
    }
    @Test void SnapshotImmutabilityAndRlsDoNotMutateExistingSchema()throws Exception{
        compositionApproved();assertThatThrownBy(()->db.update("update stem_p5_snapshots set assurance='BINDING_ONLY' where project_id=?",project)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->db.update("delete from stem_p5_snapshots where project_id=?",project)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(db.queryForObject("select count(*) from pg_class where relname in ('stem_p5_snapshots','stem_p5_compatibility') and relrowsecurity",Integer.class)).isEqualTo(2);
    }
    @Test void AdminWithoutReviewerSeatCannotAttachVideoAssessment()throws Exception{
        assertThat(call("POST",path("submit-video-review"),Map.of(),producer).statusCode()).isEqualTo(200);
        String brief=UUID.randomUUID().toString();db.update("insert into stem_briefs(id,version_id,payload) values(?,?,?::jsonb)",brief,version,JSON.writeValueAsString(com.stemotion.domain.stem.StemDtos.empty()));
        var assessment=Map.of("expectedBriefRevision",0,"expectedScriptRevision",0,"expectedModeRevision",0,"expectedCompositionRevision",1,"criteria",List.of(Map.of("key","TEST_ONLY","judgment","NOT_ASSESSED","rationale","TEST_ONLY not expert feedback","evidenceIds",List.of())),"idempotencyKey","p5-"+UUID.randomUUID());
        var r=call("POST",path("approve-video"),Map.of("note","TEST_ONLY","stemAssessment",assessment),admin);assertThat(r.statusCode()).isEqualTo(403);assertThat(count("APPROVAL")).isZero();assertThat(count("RENDER_INPUT")).isZero();
    }
    ObjectNode assessment()throws Exception{
        String brief=UUID.randomUUID().toString();db.update("insert into stem_briefs(id,version_id,payload) values(?,?,?::jsonb)",brief,version,JSON.writeValueAsString(com.stemotion.domain.stem.StemDtos.empty()));
        ObjectNode a=JSON.createObjectNode().put("expectedBriefRevision",0).put("expectedScriptRevision",0).put("expectedModeRevision",0).put("expectedCompositionRevision",1).put("idempotencyKey","p5-"+UUID.randomUUID());
        a.putArray("criteria").add(JSON.createObjectNode().put("key","TEST_ONLY").put("judgment","NOT_ASSESSED").put("rationale","TEST_ONLY not expert review").set("evidenceIds",JSON.createArrayNode()));return a;
    }
    @Test void ConcurrentKeyedApproveCreatesOneDecisionAssessmentSnapshotAndJob()throws Exception{
        call("POST",path("submit-video-review"),Map.of(),producer);JsonNode a=assessment();var body=Map.of("note","TEST_ONLY","stemAssessment",a);
        var first=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return call("POST",path("approve-video"),body,reviewer).statusCode();}catch(Exception e){throw new RuntimeException(e);}});
        var second=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return call("POST",path("approve-video"),body,reviewer).statusCode();}catch(Exception e){throw new RuntimeException(e);}});
        assertThat(first.get()).isEqualTo(204);assertThat(second.get()).isEqualTo(204);assertThat(count("APPROVAL")).isEqualTo(1);assertThat(count("RENDER_INPUT")).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from stem_assessments where version_id=?",Integer.class,version)).isEqualTo(1);
        assertThat(call("POST",path("approve-video"),Map.of("note","TEST_ONLY changed","stemAssessment",a),reviewer).statusCode()).isEqualTo(409);
    }
    @Test void G06ModeChangeBetweenLoadAndVideoApprovalRollsBackAllWrites()throws Exception{
        call("POST",path("submit-video-review"),Map.of(),producer);JsonNode a=assessment();
        var settings=Map.of("baseRevision",0,"mode","ADVISORY","policyVersionIds",List.of(),"idempotencyKey","p5-"+UUID.randomUUID());
        assertThat(call("PUT","/workspaces/"+workspace+"/stem-settings",settings,admin).statusCode()).isEqualTo(200);
        assertThat(call("POST",path("approve-video"),Map.of("note","TEST_ONLY","stemAssessment",a),reviewer).statusCode()).isEqualTo(409);assertThat(count("APPROVAL")).isZero();assertThat(count("RENDER_INPUT")).isZero();
    }
    @Test void FrozenInputTamperIsKnownMismatchAndCannotUseLegacyFallback()throws Exception{
        String job=compositionApproved();JsonNode m=manifest(job);db.update("update render_jobs set input_props=jsonb_set(input_props,'{title}','\"TEST_ONLY tampered\"') where id=?",job);
        assertThat(callback(job,m,"https://example.invalid/changed.mp4").statusCode()).isEqualTo(409);assertThat(count("CLIP")).isZero();
    }
    @Test void OldCallbackCannotReplaceCurrentUrlAfterCompositionChanged()throws Exception{
        String job=compositionApproved();JsonNode m=manifest(job);db.update("update compositions set revision=revision+1 where project_id=?",project);
        assertThat(callback(job,m,"https://example.invalid/old.mp4").statusCode()).isEqualTo(200);assertThat(count("CLIP")).isEqualTo(1);
        assertThat(db.queryForObject("select video_url from clip_projects where id=?",String.class,project)).isNull();
    }
    @Test void StatusReadIsAuthorizedAndNeverClaimsVerified()throws Exception{
        String job=compositionApproved();callback(job,manifest(job),"https://example.invalid/"+project+".mp4");
        var r=call("GET",path("stem-artifact-status"),null,writer);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(node(r).path("verificationStatus").asText()).isEqualTo("UNVERIFIED");assertThat(node(r).path("snapshotOrigin").path("snapshotId").asText()).isNotBlank();
        assertThat(call("GET",path("stem-artifact-status"),null,null).statusCode()).isIn(401,403);
    }
    @Test void P5RlsDeniesClientEvenWithExplicitSelectGrant()throws Exception{
        String role="p5_client_"+UUID.randomUUID().toString().replace("-","");db.execute("create role "+role+" noinherit");db.execute("grant usage on schema public to "+role);db.execute("grant select on stem_p5_snapshots,stem_p5_compatibility to "+role);
        compositionApproved();
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(s->{db.execute("set local role "+role);assertThat(db.queryForObject("select count(*) from stem_p5_snapshots",Integer.class)).isZero();return null;});
    }
    @Test void AT21MeasuredTimingPreservesTechnicalAssessmentButSemanticEditNeedsNewReview()throws Exception{
        db.update("update script_versions set status='DRAFT' where id=?",version);db.update("update clip_projects set script_status='DRAFT' where id=?",project);
        ObjectNode a=assessment();a.remove("expectedCompositionRevision");
        assertThat(call("POST",path("submit-review"),Map.of(),writer).statusCode()).isEqualTo(200);
        var approved=call("POST",path("approve"),Map.of("note","TEST_ONLY not expert approval","stemAssessment",a),reviewer);assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.execute(s->{
            var p=context.getBean(ClipProjectRepository.class).findById(project).orElseThrow();var v=context.getBean(ScriptVersionRepository.class).findById(version).orElseThrow();
            var compositions=context.getBean(com.stemotion.domain.production.service.CompositionService.class);var composition=compositions.ensure(p,v,producerId);
            Map<String,Integer> frames=new HashMap<>();db.queryForList("select id from scenes where version_id=?",String.class,version).forEach(id->frames.put(id,330));
            compositions.applyMeasured(project,version,composition.getRevision(),frames,Map.of(),producerId);return null;
        });
        assertThat(db.queryForObject("select count(*) from stem_p5_compatibility p join stem_p5_snapshots s on s.id=p.after_id where s.project_id=? and p.result='COMPATIBLE'",Integer.class,project)).isEqualTo(1);
        var status=node(call("GET",path("stem-artifact-status"),null,writer));assertThat(status.path("freshness").asText()).isEqualTo("CURRENT");assertThat(status.path("verificationStatus").asText()).isEqualTo("UNVERIFIED");
        var composition=node(call("GET",path("composition"),null,producer));ArrayNode scenes=(ArrayNode)composition.path("scenes");((ObjectNode)scenes.get(0)).put("title","TEST_ONLY changed semantic question");
        var save=call("PUT",path("composition"),Map.of("baseRevision",composition.path("revision").asInt(),"scenes",scenes),producer);assertThat(save.statusCode()).as(save.body()).isEqualTo(200);
        assertThat(node(call("GET",path("stem-artifact-status"),null,writer)).path("freshness").asText()).isEqualTo("NEEDS_REVIEW");
    }
    @Test void P5TransportBoundsRejectServerClaimsAndWhitespaceBeforeMutation()throws Exception{
        ObjectNode body=publishBody(true);body.put("verified",true);
        var claim=call("POST",path("publish"),body,producer);assertThat(claim.statusCode()).isEqualTo(400);assertThat(claim.body()).contains("STEM_INVALID_FIELD").contains("verified");
        var request=HttpRequest.newBuilder(URI.create(base+path("publish"))).header("Content-Type","application/json").header("Authorization","Bearer "+producer).POST(HttpRequest.BodyPublishers.ofString(" ".repeat(65537))).build();
        assertThat(HTTP.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);assertThat(count("PUBLICATION")).isZero();
    }
    @Test void NewMigrationRejectsWrongParentTypeWithoutChangingPublicSchema()throws Exception{
        String schema="p5_wrong_"+UUID.randomUUID().toString().replace("-","");db.execute("create schema "+schema);db.execute("create table "+schema+".users(id uuid primary key)");
        String sql=java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/db/stem-p5/V92000__stem_p5_artifact_snapshots_test_only.sql"));
        assertThatThrownBy(()->new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(s->{db.execute("set local search_path="+schema+",pg_catalog");db.execute(sql);return null;})).isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("P5 prerequisite drift");
        assertThat(db.queryForObject("select to_regclass(?)::text",String.class,schema+".stem_p5_snapshots")).isNull();assertThat(db.queryForObject("select count(*) from flyway_schema_history where success",Integer.class)).isEqualTo(28);
    }
    @Test void B03RetryHasNewAttemptAndOldCompletionCannotWin()throws Exception{
        String old=compositionApproved();JsonNode oldManifest=manifest(old);
        ObjectNode failed=JSON.createObjectNode().put("jobId",old).put("status","FAILED").put("errorMessage","TEST_ONLY transient fake failure");
        var request=HttpRequest.newBuilder(URI.create(base+"/render/callback")).header("Content-Type","application/json").header("X-Render-Api-Key","e2e-worker-key").POST(HttpRequest.BodyPublishers.ofString(failed.toString())).build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String next=node(response).path("jobId").asText();assertThat(next).isNotEqualTo(old);assertThat(db.queryForObject("select attempt_count from render_jobs where id=?",Integer.class,next)).isEqualTo(2);
        assertThat(manifest(next).path("renderInputHash")).isEqualTo(oldManifest.path("renderInputHash"));assertThat(manifest(next).path("attemptToken")).isNotEqualTo(oldManifest.path("attemptToken"));
        assertThat(callback(old,oldManifest,"https://example.invalid/old-attempt.mp4").statusCode()).isEqualTo(200);assertThat(count("CLIP")).isZero();
        assertThat(callback(next,manifest(next),"https://example.invalid/"+project+".mp4").statusCode()).isEqualTo(200);assertThat(count("CLIP")).isEqualTo(1);
        assertThat(call("POST",path("publish"),publishBody(false),producer).statusCode()).isEqualTo(201);
    }
}
