package com.stemotion.e2e;

import com.fasterxml.jackson.databind.*;
import com.stemotion.domain.ai.entity.AiRequest;
import com.stemotion.domain.ai.repository.AiRequestRepository;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.integration.client.GeminiClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real independent audit transactions on a loopback-only fixture database. No production/provider calls. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/postgres",
    "spring.datasource.username=stemotion_smoke","spring.datasource.password=",
    "spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.hikari.data-source-properties.stringtype=unspecified",
    "app.integration.ai.mode=embedded","logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="STEMOTION_LOCAL_E2E",matches="true")
class LocalAiDecisionE2eTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired AiRequestRepository requests;
    @Autowired PasswordEncoder encoder;
    @MockBean GeminiClient gemini;
    final List<String> actorIds=new ArrayList<>();
    final List<String> requestIds=new ArrayList<>();
    String owner,other,admin;
    @BeforeEach void fixtures() throws Exception {
        owner=login(User.UserRole.WRITER);other=login(User.UserRole.WRITER);admin=login(User.UserRole.ADMIN);
        when(gemini.generateWithUsage(anyString(),anyString(),anyMap()))
            .thenReturn(new GeminiClient.UsageResult(json.readTree("{\"readabilityScore\":80}"),12,8,20));
    }
    String login(User.UserRole role) throws Exception {
        var user=users.saveAndFlush(User.builder().fullName("Local AI").email(UUID.randomUUID()+"@example.invalid")
            .role(role).passwordHash(encoder.encode("Local-fixture-only-123")).build());actorIds.add(user.getId());
        return call("POST","/api/v1/auth/login",null,Map.of("email",user.getEmail(),"password","Local-fixture-only-123"),200).path("accessToken").asText();
    }
    JsonNode call(String method,String path,String token,Object body,int expected) throws Exception {
        var builder=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),path);
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(body!=null)builder.contentType("application/json").content(json.writeValueAsString(body));
        var response=mvc.perform(builder).andReturn().getResponse();
        assertEquals(expected,response.getStatus(),response.getContentAsString());
        return json.readTree(response.getContentAsString());
    }
    String generate() throws Exception {
        String id=call("POST","/api/v1/ai/script/action",owner,Map.of("action","grade","scriptText","Force changes motion"),200).path("requestId").asText();
        assertFalse(id.isBlank());requestIds.add(id);return id;
    }
    @Test void acceptsOwnedSuggestionWithRealUsageAndIdempotency() throws Exception {
        String id=generate();var r=requests.findById(id).orElseThrow();
        assertEquals(AiRequest.RequestType.ANALYSIS,r.getRequestType());
        assertEquals(AiRequest.CallStatus.SUCCEEDED,r.getCallStatus());
        assertEquals(12,r.getInputTokens());assertEquals(8,r.getOutputTokens());assertEquals(20,r.getCostTokens());
        assertNotNull(r.getModel());assertTrue(r.getLatencyMs()>=0);assertNull(r.getMonetaryCost());assertNull(r.getCostCurrency());
        decision(id,other,"ACCEPTED",403);decision(id,null,"ACCEPTED",401);
        decision(id,owner,"PENDING",400);decision(id,owner,"ACCEPTED",200);decision(id,owner,"ACCEPTED",200);
        decision(id,owner,"IGNORED",409);assertEquals(AiRequest.Decision.ACCEPTED,requests.findById(id).orElseThrow().getDecision());
    }
    @Test void ignorePersistsAndAdminCanDecide() throws Exception {
        String id=generate();decision(id,admin,"IGNORED",200);
        assertEquals(AiRequest.Decision.IGNORED,requests.findById(id).orElseThrow().getDecision());
    }
    @Test void failureSurvivesCallerErrorAndCannotBeAccepted() throws Exception {
        when(gemini.generateWithUsage(anyString(),anyString(),anyMap())).thenThrow(
            new com.stemotion.core.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"AI_CALL_FAILED","Sanitized provider failure"));
        call("POST","/api/v1/ai/script/action",owner,Map.of("action","grade","scriptText","Force"),502);
        var r=requests.findAll().stream().filter(x->x.getRequester()!=null && actorIds.contains(x.getRequester().getId())).findFirst().orElseThrow();
        requestIds.add(r.getId());assertEquals(AiRequest.CallStatus.FAILED,r.getCallStatus());
        assertNull(r.getInputTokens());assertNull(r.getOutputTokens());assertNull(r.getCostTokens());assertNull(r.getMonetaryCost());
        assertEquals("AI_CALL_FAILED",json.readTree(r.getResponseData()).path("error").asText());decision(r.getId(),owner,"ACCEPTED",409);
    }
    @Test void missingUsageStaysUnknown() throws Exception {
        when(gemini.generateWithUsage(anyString(),anyString(),anyMap())).thenReturn(new GeminiClient.UsageResult(json.readTree("{}"),null,null,null));
        var r=requests.findById(generate()).orElseThrow();assertNull(r.getCostTokens());assertNull(r.getMonetaryCost());
    }
    void decision(String id,String token,String value,int code) throws Exception {
        call("PUT","/api/v1/ai/requests/"+id+"/decision",token,Map.of("decision",value),code);
    }
    @AfterEach void cleanup() {
        requests.deleteAllById(requestIds);users.deleteAllById(actorIds);
    }
}
