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

import static com.stemotion.e2e.StemArtifactP5Test.*;
import com.stemotion.domain.stem.*;
import com.stemotion.domain.ai.service.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** P6 owns its context/processes; reuses P5 fixture helpers read-only, never inherits its test methods. */
class StemP6HttpTest {
    static Process python;static String pythonUrl;StemArtifactP5Test fixture;String outcome;
    static Path resolvePythonBinary() {
        String p6 = System.getenv("P6_PYTHON");
        if (p6 != null && !p6.isBlank()) {
            Path p = Path.of(p6);
            if (Files.isRegularFile(p)) return p;
        }
        Path repo = Path.of(System.getProperty("user.dir"));
        Path win = repo.resolve(".audit/stem-p6/exact-lock/venv/Scripts/python.exe");
        if (Files.isRegularFile(win)) return win;
        Path unix = repo.resolve(".audit/stem-p6/exact-lock/venv/bin/python");
        if (Files.isRegularFile(unix)) return unix;
        return null;
    }
    static void startPython(Path pythonBinary)throws Exception{
        Path repo=Path.of(System.getProperty("user.dir")),audit=repo.resolve(".audit/stem-p6/http-ai-"+System.getenv().getOrDefault("P6_RUN_NAME","new"));Files.createDirectories(audit);
        int port;try(var socket=new java.net.ServerSocket(0,0,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
        pythonUrl="http://127.0.0.1:"+port;
        var builder=new ProcessBuilder(pythonBinary.toString(),"-m","uvicorn","p6_fake_server:app","--host","127.0.0.1","--port",Integer.toString(port),"--no-access-log");
        builder.directory(repo.resolve("services/ai").toFile());var env=builder.environment();env.keySet().removeIf(k->!Set.of("SYSTEMROOT","WINDIR","COMSPEC","PATHEXT","USERPROFILE","APPDATA","LOCALAPPDATA","TEMP","TMP","PATH","HOMEDRIVE","HOMEPATH").contains(k.toUpperCase()));
        env.put("AI_SERVICE_KEY","TEST_ONLY-p6-transport");env.put("HF_HUB_OFFLINE","1");env.put("PYTHONNOUSERSITE","1");env.put("PYTHONPATH",repo.resolve("services/ai")+java.io.File.pathSeparator+repo.resolve("services/ai/tests"));
        builder.redirectErrorStream(true).redirectOutput(audit.resolve("python.log").toFile());python=builder.start();Files.writeString(audit.resolve("owned-pid.json"),"{\"pid\":"+python.pid()+",\"port\":"+port+"}");
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);boolean ready=false;
        while(System.nanoTime()<deadline&&python.isAlive()){try{if(HTTP.send(HttpRequest.newBuilder(URI.create(pythonUrl+"/health")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()==200){ready=true;break;}}catch(Exception ignored){}Thread.sleep(100);}
        if(!ready){python.destroy();throw new IllegalStateException("Owned Python fake server did not become ready");}
    }
    @BeforeAll static void boot() throws Exception {
        Path pythonBinary = resolvePythonBinary();
        org.junit.jupiter.api.Assumptions.assumeTrue(pythonBinary != null, "P6 virtualenv not found; skipping StemP6HttpTest");
        startPython(pythonBinary);
        System.setProperty("spring.devtools.restart.enabled","false");
        context=new SpringApplication(StemotionApplication.class,E2eServer.Config.class).run(E2eServer.launchArguments(
            "--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=stem-p5-test","--spring.dotenv.enabled=false",
            "--spring.flyway.locations=classpath:db/migration,classpath:db/auth-v2-e2e,classpath:db/stem-p3,classpath:db/stem-p5",
            "--app.stem.enabled=true","--app.stem.artifact.enabled=true","--app.notification-email.enabled=false",
            "--app.integration.render.max-attempts=3",
            "--app.integration.ai.mode=remote","--app.integration.ai.base-url="+pythonUrl,"--app.integration.ai.api-key=TEST_ONLY-p6-transport","--app.integration.ai.timeout-seconds=2",
            "--app.integration.render.callback-url=http://127.0.0.1:9/unused-p5-test-callback",
            "--logging.level.root=WARN","--logging.level.com.stemotion=WARN","--logging.level.org.hibernate.SQL=WARN",
            "--logging.level.io.zonky.test.db.postgres.embedded.EmbeddedPostgres=INFO"));
        base="http://127.0.0.1:"+((ServletWebServerApplicationContext)context).getWebServer().getPort()+"/api/v1";db=context.getBean(JdbcTemplate.class);
        assertThat(db.queryForObject("select count(*) from flyway_schema_history where success",Integer.class)).isEqualTo(26);
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        List<String> ids=tx.execute(s->{List<String> result=new ArrayList<>();for(String role:List.of("writer","reviewer","producer"))result.add(context.getBean(UserRepository.class).saveAndFlush(User.builder().fullName("TEST_ONLY "+role).username("p5-"+role).passwordHash(context.getBean(PasswordEncoder.class).encode("P5-fixture-only-123")).systemRole(SystemRole.USER).status(User.UserStatus.ACTIVE).mustChangePassword(false).build()).getId());return result;});
        writerId=ids.get(0);reviewerId=ids.get(1);producerId=ids.get(2);
        writer=login("p5-writer","P5-fixture-only-123");reviewer=login("p5-reviewer","P5-fixture-only-123");producer=login("p5-producer","P5-fixture-only-123");admin=login("e2e-admin","E2e-fixture-only-123");
    }

    @AfterAll static void cleanup()throws Exception{try{if(context!=null)context.close();}finally{if(python!=null){python.destroy();if(!python.waitFor(15,TimeUnit.SECONDS)){python.destroyForcibly();python.waitFor(10,TimeUnit.SECONDS);}}}}
    @BeforeEach void prepare()throws Exception{
        fixture=new StemArtifactP5Test();fixture.seed();var repo=context.getBean(StemRepository.class);
        String source=repo.createDraft(StemModels.Kind.SOURCE_EDITION,"TEST_ONLY-SOURCE-"+UUID.randomUUID(),1,null,null,JSON.createObjectNode().put("title","TEST_ONLY not official"));
        String profile=repo.createDraft(StemModels.Kind.PROFILE,"TEST_ONLY-PROFILE-"+UUID.randomUUID(),1,null,source,JSON.createObjectNode().put("gradeKey","4").put("educationLevel","PRIMARY"));
        outcome=repo.createDraft(StemModels.Kind.OUTCOME,"TEST_ONLY-OUTCOME-"+UUID.randomUUID(),1,profile,source,JSON.createObjectNode().put("gradeKey","4").put("subjectKey","TEST_ONLY_SCIENCE"));
        var b=StemDtos.empty();var brief=new StemDtos.Brief(null,null,"4",List.of("TEST_ONLY_SCIENCE"),List.of(outcome),"TEST_ONLY clip + task card",new StemDtos.UsageContext(new StemDtos.ContextEntry("IN_CLASS_SCREEN","TEST_ONLY","TEST_ONLY"),List.of(new StemDtos.ContextEntry("GROUP_PRACTICE","TEST_ONLY condition","TEST_ONLY adaptation"))),List.of("DEPARTMENT_LIBRARY"),List.of(new StemDtos.Objective("o1",outcome,"TEST_ONLY observe","TEST_ONLY record")),List.of(new StemDtos.Task("t1","TEST_ONLY compare","TEST_ONLY adult support","TEST_ONLY paper","TEST_ONLY safe fixture","TEST_ONLY record","GROUP_PRACTICE")),List.of(),"TEST_ONLY teacher review required",null);
        db.update("insert into stem_briefs(id,version_id,payload) values(?,?,?::jsonb)",UUID.randomUUID().toString(),fixture.version,JSON.writeValueAsString(brief));
    }
    void draft(){db.update("update script_versions set status='DRAFT' where id=?",fixture.version);db.update("update clip_projects set script_status='DRAFT' where id=?",fixture.project);}
    ObjectNode request(String op){return JSON.createObjectNode().put("schemaVersion","stem-ai.v1").put("operation",op).put("scriptVersionId",fixture.version).put("expectedScriptRevision",0).put("expectedBriefRevision",0).put("expectedModeRevision",0);}
    String grounded(){return "/ai/scripts/"+fixture.project+"/grounded";}
    void publish()throws Exception{String job=fixture.compositionApproved();assertThat(fixture.callback(job,fixture.manifest(job),"https://example.invalid/"+fixture.project+".mp4").statusCode()).isEqualTo(200);var response=call("POST",fixture.path("publish"),fixture.publishBody(false),producer);assertThat(response.statusCode()).as(response.body()).isEqualTo(201);}
    @Test void AI01RealJavaPythonGroundingIsUnverifiedAndDoesNotWriteDraft()throws Exception{
        draft();var before=db.queryForMap("select draft_revision,status from script_versions where id=?",fixture.version);
        for(String op:List.of("generate","resegment","grade","extract","terms")){
            var r=call("POST",grounded(),request(op),writer);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(node(r).path("grounding").path("verificationStatus").asText()).isEqualTo("UNVERIFIED");assertThat(node(r).path("grounding").path("objectiveKeys").get(0).asText()).isEqualTo("o1");
        }
        assertThat(db.queryForMap("select draft_revision,status from script_versions where id=?",fixture.version)).isEqualTo(before);
        var probe=JSON.readTree(HTTP.send(HttpRequest.newBuilder(URI.create(pythonUrl+"/_p6/probe")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        var c=context.getBean(StemAiContextResolver.class).resolve(fixture.project,writerId,new StemAiContextResolver.Target(fixture.version,0,0,0));assertThat(probe.path("instruction").asText()).isEqualTo(StemGroundedAiService.instruction(c.context()));assertThat(probe.path("prompt").asText()).endsWith(StemGroundedAiService.contextSection(c.context()));
    }
    @Test void AI02AcceptOnlyLogsAndStaleAcceptIsRejected()throws Exception{
        draft();var r=call("POST",grounded(),request("terms"),writer);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);var data=node(r);var g=data.path("grounding");var bind=JSON.createObjectNode().put("projectId",fixture.project).put("contextDigest",g.path("contextDigest").asText()).set("target",g.path("target"));
        String id=data.path("requestId").asText();assertThat(call("PUT","/ai/requests/"+id+"/decision",Map.of("decision","ACCEPTED","grounding",bind),writer).statusCode()).isEqualTo(200);
        assertThat(db.queryForObject("select draft_revision from script_versions where id=?",Integer.class,fixture.version)).isZero();
        db.update("update stem_briefs set revision=revision+1 where version_id=?",fixture.version);
        assertThat(call("PUT","/ai/requests/"+id+"/decision",Map.of("decision","ACCEPTED","grounding",bind),writer).statusCode()).isEqualTo(409);
    }
    @Test void AI02MissingBindingCannotDecideGroundedRequest()throws Exception{draft();var r=call("POST",grounded(),request("terms"),writer);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(call("PUT","/ai/requests/"+node(r).path("requestId").asText()+"/decision",Map.of("decision","ACCEPTED"),writer).statusCode()).isEqualTo(400);}
    @Test void AI01ForeignActorAndStaleRevisionRejectedBeforeProvider()throws Exception{draft();assertThat(call("POST",grounded(),request("terms"),producer).statusCode()).isEqualTo(403);var body=request("terms").put("expectedBriefRevision",99);assertThat(call("POST",grounded(),body,writer).statusCode()).isEqualTo(409);String foreignVersion=fixture.version;prepare();draft();assertThat(call("POST",grounded(),request("terms").put("scriptVersionId",foreignVersion),writer).statusCode()).isEqualTo(403);}
    @Test void AI01MalformedBoundedRequestsCannotReachProvider()throws Exception{
        draft();assertThat(call("POST",grounded(),request("terms").put("expectedBriefRevision",-1),writer).statusCode()).isEqualTo(400);
        var raw=HttpRequest.newBuilder(URI.create(base+grounded())).header("Content-Type","application/json").header("Authorization","Bearer "+writer).POST(HttpRequest.BodyPublishers.ofString(" ".repeat(262145))).build();assertThat(HTTP.send(raw,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
        assertThat(call("POST","/ai/script/generate",Map.of("topic","TEST_ONLY","subject","Math","gradeLevel","4","stemContext",Map.of("verificationStatus","VERIFIED")),writer).statusCode()).isEqualTo(400);
    }
    @Test void AI01ClientClaimsAndUnsupportedOperationRejected()throws Exception{draft();var body=request("terms").put("verificationStatus","VERIFIED");assertThat(call("POST",grounded(),body,writer).statusCode()).isEqualTo(400);assertThat(call("POST",grounded(),request("metaphor"),writer).statusCode()).isEqualTo(400);}
    @Test void AI01MissingOutcomeIsNotModelAuthority()throws Exception{draft();db.update("update stem_briefs set payload=jsonb_set(payload,'{outcomeIds}',?::jsonb) where version_id=?","[\"TEST_ONLY-missing\"]",fixture.version);assertThat(call("POST",grounded(),request("terms"),writer).statusCode()).isEqualTo(400);}
    @Test void AI02ProviderFailureLeavesDraftEditable()throws Exception{draft();db.update("update stem_briefs set payload=jsonb_set(payload,'{safetyNotes}','\"TEST_ONLY_TIMEOUT\"') where version_id=?",fixture.version);var r=call("POST",grounded(),request("terms"),writer);assertThat(r.statusCode()).as(r.body()).isEqualTo(504);assertThat(db.queryForObject("select status::text from script_versions where id=?",String.class,fixture.version)).isEqualTo("DRAFT");}
    @Test void AI02InvalidModelClaimsDoNotGrantVerification()throws Exception{draft();db.update("update stem_briefs set payload=jsonb_set(payload,'{safetyNotes}','\"TEST_ONLY_BAD_OUTPUT\"') where version_id=?",fixture.version);assertThat(call("POST",grounded(),request("terms"),writer).statusCode()).isEqualTo(502);}
    @Test void AI02ContextChangedDuringRealPythonCallIsRejected()throws Exception{
        draft();db.update("update stem_briefs set payload=jsonb_set(payload,'{safetyNotes}','\"TEST_ONLY_INFLIGHT\"') where version_id=?",fixture.version);
        var body=request("terms");String path=grounded();
        var pending=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return call("POST",path,body,writer);}catch(Exception e){throw new RuntimeException(e);}});
        boolean started=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline){var probe=HTTP.send(HttpRequest.newBuilder(URI.create(pythonUrl+"/_p6/probe")).GET().build(),HttpResponse.BodyHandlers.ofString());if(probe.body().contains("TEST_ONLY_INFLIGHT")){started=true;break;}Thread.sleep(20);}
        assertThat(started).isTrue();db.update("update stem_briefs set revision=revision+1 where version_id=?",fixture.version);
        assertThat(pending.get(10,TimeUnit.SECONDS).statusCode()).isEqualTo(409);assertThat(db.queryForObject("select draft_revision from script_versions where id=?",Integer.class,fixture.version)).isZero();
    }
    @Test void AI02BusyProviderReturns429AndDoesNotRetry()throws Exception{
        draft();int before=db.queryForObject("select count(*) from ai_requests",Integer.class);db.update("update stem_briefs set payload=jsonb_set(payload,'{safetyNotes}','\"TEST_ONLY_BUSY\"') where version_id=?",fixture.version);
        assertThat(call("POST",grounded(),request("terms"),writer).statusCode()).isEqualTo(429);assertThat(db.queryForObject("select count(*) from ai_requests",Integer.class)).isEqualTo(before+1);
    }
    @Test void L01CombinedFrozenFiltersAndPagingHeadersAgree()throws Exception{
        publish();String query="?educationLevel=PRIMARY&grade=4&subjectId=TEST_ONLY_SCIENCE&outcomeId="+outcome+"&usageContext=GROUP_PRACTICE&contextRole=SECONDARY&channel=DEPARTMENT_LIBRARY&page=0&size=1";
        var r=call("GET","/library/clips"+query,null,writer);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);assertThat(r.headers().firstValue("X-Total-Count")).contains("1");assertThat(node(r)).hasSize(1);assertThat(node(r).get(0).path("learningCard").path("studentTasks").get(0).path("key").asText()).isEqualTo("t1");
        assertThat(node(call("GET","/library/clips"+query.replace("page=0","page=1"),null,writer))).isEmpty();
        assertThat(node(call("GET","/library/clips?outcomeId="+outcome+"&usageContext=GROUP_PRACTICE&contextRole=PRIMARY",null,writer))).isEmpty();
    }
    @Test void L01PublishedCardAndFacetsIgnoreMutableBriefProject()throws Exception{
        publish();var before=node(call("GET","/library/clips/"+fixture.project,null,writer));db.update("update clip_projects set title='TEST_ONLY changed',grade_level='11' where id=?",fixture.project);db.update("update stem_briefs set payload='{}'::jsonb where version_id=?",fixture.version);
        var after=node(call("GET","/library/clips/"+fixture.project,null,writer));assertThat(after.path("learningCard")).isEqualTo(before.path("learningCard"));assertThat(after.path("gradeLevel").asText()).isEqualTo("4");
        var facets=call("GET","/library/filters?outcomeId="+outcome,null,writer);assertThat(facets.statusCode()).as(facets.body()).isEqualTo(200);assertThat(node(facets).path("gradeLevels").toString()).contains("4").doesNotContain("11");
    }
    @Test void L01InvalidFiltersAndInjectionCannotChangePredicate()throws Exception{publish();assertThat(call("GET","/library/clips?grade=4&gradeLevel=11",null,writer).statusCode()).isEqualTo(400);assertThat(call("GET","/library/clips?usageContext=DEPARTMENT_LIBRARY",null,writer).statusCode()).isEqualTo(400);assertThat(node(call("GET","/library/clips?outcomeId="+java.net.URLEncoder.encode("' OR 1=1 --",java.nio.charset.StandardCharsets.UTF_8),null,writer))).isEmpty();}
    @Test void L02LegacyRemainsReadableAndCannotMatchCurriculumFilter()throws Exception{db.update("delete from stem_briefs where version_id=?",fixture.version);publish();var item=node(call("GET","/library/clips/"+fixture.project,null,writer));assertThat(item.path("verificationStatus").asText()).isEqualTo("LEGACY_UNMAPPED");assertThat(item.path("videoUrl").asText()).isNotBlank();assertThat(item.path("learningCard").path("gradeKey").isMissingNode()).isTrue();}
    @Test void P6FrontendIntegrationUsesThisRealBackendAndPython()throws Exception{
        publish();String publishedOutcome=outcome;prepare();draft();
        Path repo=Path.of(System.getProperty("user.dir")),fe=repo.getParent().getParent().resolve("phase-04/STEMotion_FE");
        Path out=repo.resolve(".audit/stem-p6/fe-live-"+System.getenv().getOrDefault("P6_RUN_NAME","new"));Files.createDirectories(out);
        String config="import react from "+JSON.writeValueAsString(fe.resolve("node_modules/@vitejs/plugin-react/dist/index.js").toUri().toString())+";\nexport default {plugins:[react()],root:"+JSON.writeValueAsString(fe.toString())+",envDir:false,cacheDir:"+JSON.writeValueAsString(out.resolve("cache").toString())+",test:{environment:'jsdom',globals:true,cache:false,fsModuleCache:false,maxWorkers:1,fileParallelism:false,include:['src/components/stem/StemFlow.p6-live.test.tsx'],reporters:['default','json'],outputFile:{json:"+JSON.writeValueAsString(out.resolve("results.json").toString())+"}}};";
        Path configPath=out.resolve("vitest.config.mjs");Files.writeString(configPath,config);
        var builder=new ProcessBuilder("node",fe.resolve("node_modules/vitest/vitest.mjs").toString(),"run","--config",configPath.toString(),"--configLoader","native");builder.directory(fe.toFile());
        var env=builder.environment();env.keySet().removeIf(k->!Set.of("SYSTEMROOT","WINDIR","COMSPEC","PATHEXT","USERPROFILE","APPDATA","LOCALAPPDATA","TEMP","TMP","PATH","HOMEDRIVE","HOMEPATH").contains(k.toUpperCase()));
        env.put("P6_LIVE_API_URL",base);env.put("P6_TEST_PROJECT",fixture.project);env.put("P6_TEST_VERSION",fixture.version);env.put("P6_TEST_OUTCOME",publishedOutcome);env.put("CI","true");env.put("NO_COLOR","1");
        var node=builder.redirectErrorStream(true).redirectOutput(out.resolve("vitest.log").toFile()).start();boolean done=node.waitFor(150,TimeUnit.SECONDS);if(!done){node.destroy();node.waitFor(10,TimeUnit.SECONDS);}assertThat(done).as("Owned P6 FE live timeout; see fe-live/vitest.log").isTrue();assertThat(node.exitValue()).as("Owned P6 FE live exit; see fe-live/vitest.log").isZero();
    }

}
