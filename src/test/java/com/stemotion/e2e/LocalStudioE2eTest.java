package com.stemotion.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.production.repository.ClipRepository;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.integration.client.GeminiClient;
import com.stemotion.integration.client.RenderServiceClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in controller/JWT/JPA integration. Never uses a shared or production database. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/postgres",
    "spring.datasource.username=stemotion_smoke", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.hikari.data-source-properties.stringtype=unspecified",
    "app.integration.ai.mode=embedded", "app.integration.render.api-key=local-e2e-worker",
    "app.integration.render.base-url=http://127.0.0.1:14000",
    "app.integration.render.callback-url=http://127.0.0.1:18081/api/v1/render/callback",
    "app.publish.embed-base-url=", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="STEMOTION_LOCAL_E2E", matches="true")
@Transactional
class LocalStudioE2eTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;
    @Autowired ClipRepository clips;
    @Autowired StatusHistoryRepository histories;
    @MockBean GeminiClient gemini;
    @MockBean RenderServiceClient worker;
    @MockBean com.stemotion.domain.ai.service.AiRequestLogService audit;
    Map<String,String> tokens=new HashMap<>();
    Map<String,User> actors=new HashMap<>();
    String workspace;

    @BeforeEach void fixtures() throws Exception {
        for (var role:User.UserRole.values()) {
            var user=users.saveAndFlush(User.builder().fullName("Local "+role)
                .email(UUID.randomUUID()+"@example.invalid").passwordHash(encoder.encode("Local-fixture-only-123"))
                .role(role).build());
            actors.put(role.name(),user);
            var login=call("POST","/api/v1/auth/login",null,Map.of("email",user.getEmail(),"password","Local-fixture-only-123"),200);
            tokens.put(role.name(),login.path("accessToken").asText());
            assertFalse(tokens.get(role.name()).isBlank());
        }
        workspace=call("POST","/api/v1/workspaces","WRITER",Map.of("name","Local E2E","subject","Physics"),201).path("id").asText();
        for (String role:List.of("REVIEWER","PRODUCER")) members.saveAndFlush(WorkspaceMember.builder()
            .workspace(workspaces.findById(workspace).orElseThrow()).user(actors.get(role))
            .invitedEmail(actors.get(role).getEmail()).role(actors.get(role).getRole()).status(MemberStatus.ACTIVE).build());
        when(gemini.isConfigured()).thenReturn(true);
        when(worker.enqueue(anyString(),anyString(),anyMap(),anyInt(),anyInt(),anyInt(),nullable(String.class)))
            .thenAnswer(i->new RenderServiceClient.EnqueueResult(i.getArgument(0),5));
    }
    JsonNode call(String method,String path,String role,Object body,int expected) throws Exception {
        var request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),path);
        if(role!=null) request.header("Authorization","Bearer "+tokens.get(role));
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        var result=mvc.perform(request).andReturn().getResponse();
        assertEquals(expected,result.getStatus(),method+" "+path+": "+result.getContentAsString());
        return result.getContentAsString().isBlank()?json.createObjectNode():json.readTree(result.getContentAsString());
    }
    Map<String,Object> scene() {
        return new HashMap<>(Map.of("type","TITLE_HERO","title","Force","narration","A force changes motion.",
            "durationInFrames",600,"subtitle","Local fixture","subject","Physics","gradeLevel","10","badgeText","STEM"));
    }
    JsonNode createProject() throws Exception {
        return call("POST","/api/v1/workspaces/"+workspace+"/scripts","WRITER",
            Map.of("title","Local E2E","subject","Physics","gradeLevel","10","fps",30,"scenes",List.of(scene())),201);
    }
    @Test void authAndRoleGates() throws Exception {
        call("GET","/api/v1/users",null,null,401);
        call("POST","/api/v1/auth/register",null,Map.of("fullName","Guest","email","guest@example.invalid","password","abcdefgh"),401);
        for(String role:List.of("WRITER","REVIEWER","PRODUCER")) call("GET","/api/v1/admin/metrics",role,null,403);
        call("GET","/api/v1/admin/metrics","ADMIN",null,200);
        var project=createProject();String id=project.path("id").asText();
        call("POST","/api/v1/scripts/"+id+"/approve","WRITER",null,403);
        call("POST","/api/v1/render/projects/"+id+"/start","REVIEWER",Map.of(),403);
        call("PUT","/api/v1/scripts/"+id,"PRODUCER",Map.of("title","Forbidden"),403);
    }
    @Test void writerReviewerProducerQaLibraryRoundTrip() throws Exception {
        var project=createProject();String id=project.path("id").asText();String sid=project.path("scenes").get(0).path("id").asText();
        String draftComment=call("POST","/api/v1/scripts/"+id+"/qa-comments","REVIEWER",Map.of("sceneId",sid,"content","Draft feedback"),201).path("id").asText();
        var updated=scene();updated.put("id",sid);updated.put("title","Edited force");
        assertEquals(sid,call("PUT","/api/v1/scripts/"+id,"WRITER",Map.of("scenes",List.of(updated)),200).path("scenes").get(0).path("id").asText());
        assertEquals(1,call("GET","/api/v1/scenes/"+sid+"/comments","REVIEWER",null,200).size());
        call("PUT","/api/v1/scripts/"+id,"WRITER",Map.of("scenes",List.of()),400);
        call("PUT","/api/v1/qa-comments/"+draftComment+"/resolve","WRITER",null,200);
        call("POST","/api/v1/scripts/"+id+"/submit-review","WRITER",null,200);
        String comment=call("POST","/api/v1/scripts/"+id+"/qa-comments","REVIEWER",Map.of("sceneId",sid,"content","Clarify"),201).path("id").asText();
        call("PUT","/api/v1/qa-comments/"+comment+"/resolve","WRITER",null,200);
        call("POST","/api/v1/scripts/"+id+"/request-changes","REVIEWER",Map.of("note","Revise"),200);
        var revised=call("GET","/api/v1/scripts/"+id,"WRITER",null,200);
        String revisionScene=revised.path("scenes").get(0).path("id").asText();
        updated.put("id",revisionScene);
        var saved=call("PUT","/api/v1/scripts/"+id,"WRITER",Map.of("scenes",List.of(updated)),200);
        assertEquals(revisionScene,saved.path("scenes").get(0).path("id").asText());
        call("POST","/api/v1/scripts/"+id+"/submit-review","WRITER",null,200);
        call("POST","/api/v1/scripts/"+id+"/approve","REVIEWER",Map.of("note","Approved"),200);
        String job=call("POST","/api/v1/render/projects/"+id+"/start","PRODUCER",Map.of("resolution","1080p","fps",30),202).path("jobId").asText();
        assertEquals("QUEUED",call("GET","/api/v1/render/status/"+job,"PRODUCER",null,200).path("status").asText());
        callback(Map.of("jobId",job,"status","PROCESSING","progress",50));
        assertEquals(50,call("GET","/api/v1/render/status/"+job,"PRODUCER",null,200).path("progress").asInt());
        callback(Map.of("jobId",job,"status","COMPLETED","progress",100,"videoUrl","https://fixture.example/video.mp4"));
        var clip=clips.findByRenderJobId(job).orElseThrow();
        assertTrue(call("GET","/api/v1/scripts/"+id+"/qa-report","REVIEWER",null,200).path("passed").asBoolean());
        call("POST","/api/v1/scripts/"+id+"/approve-video","REVIEWER",Map.of("note","Clip approved"),204);
        var pub=call("POST","/api/v1/scripts/"+id+"/publish","PRODUCER",Map.of("targets",List.of("INTERNAL_LIBRARY","LMS"),"title","Force"),201);
        assertTrue(pub.path("lmsEmbedCode").asText().contains("fixture.example/video.mp4"));
        call("GET","/api/v1/library/clips/"+id,"WRITER",null,200);
        var decisions=histories.findByProjectIdOrderByCreatedAtDesc(id).stream().filter(h->h.getDecision()!=null).toList();
        assertEquals(3,decisions.size());
        assertTrue(decisions.stream().anyMatch(h->h.getTargetId().equals(clip.getId()) && "APPROVED".equals(h.getDecision().name())));
        assertTrue(decisions.stream().anyMatch(h->"REJECTED".equals(h.getDecision().name()) && "Revise".equals(h.getReason())));
    }
    void callback(Object body) throws Exception {
        var r=mvc.perform(MockMvcRequestBuilders.post("/api/v1/render/callback").header("X-Render-Api-Key","local-e2e-worker")
            .contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse();
        assertEquals(200,r.getStatus(),r.getContentAsString());
    }
    @Test void aiStubAnalysisBaseline() throws Exception {
        when(gemini.generateWithUsage(anyString(),anyString(),anyMap())).thenReturn(new GeminiClient.UsageResult(json.readTree("{\"score\":80}"),null,null,null));
        var r=call("POST","/api/v1/ai/script/action","WRITER",Map.of("action","grade","scriptText","Force changes motion","subject","Physics","gradeLevel","10"),200);
        assertEquals(80,r.path("result").path("score").asInt());
        // accept/ignore and persisted logging are the M01 baseline gap, tested after Track 2.
    }
    @Test void adminProvisionTemplateCrudAndQueue() throws Exception {
        call("POST","/api/v1/users","ADMIN",Map.of("fullName","Provisioned","email",UUID.randomUUID()+"@example.invalid","password","Local-fixture-only-123","role","PRODUCER"),201);
        var metadata=Map.of("name","E2E-"+UUID.randomUUID(),"subject","physics","assetPack","physics@1.0.0","slots","{}","propsSchema","{}","isActive",true);
        String id=call("POST","/api/v1/templates","ADMIN",metadata,201).path("id").asText();
        assertEquals("physics",call("GET","/api/v1/templates/"+id,"WRITER",null,200).path("subject").asText());
        call("PUT","/api/v1/templates/"+id,"ADMIN",metadata,200);
        call("DELETE","/api/v1/templates/"+id,"ADMIN",null,204);
        call("GET","/api/v1/admin/metrics","ADMIN",null,200);
    }
}
