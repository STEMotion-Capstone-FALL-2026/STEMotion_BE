package com.stemotion.domain.stem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.*;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import com.stemotion.domain.workspace.service.WorkspaceService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "app.stem.enabled=true",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@ActiveProfiles("stem-p3-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({StemRepository.class, StemValidator.class, StemService.class, WorkspaceAccess.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StemDemoCatalogAcceptanceTest {

  static EmbeddedPostgres pg;

  @TestConfiguration
  static class Config {
    @Bean
    DataSource dataSource() throws Exception {
      pg = EmbeddedPostgres.builder().start();
      return pg.getDatabase("postgres", "postgres", Map.of("stringtype", "unspecified"));
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @AfterAll
  static void stop() throws Exception {
    if (pg != null) pg.close();
  }

  @Autowired StemService stem;
  @Autowired StemRepository db;
  @Autowired StemValidator validator;
  @Autowired WorkspaceAccess access;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager manager;
  @Autowired UserRepository users;
  @Autowired WorkspaceRepository workspaces;
  @Autowired WorkspaceMemberRepository members;
  @Autowired ClipProjectRepository projects;
  @Autowired ScriptVersionRepository versions;
  @Autowired SceneRepository scenes;
  @Autowired StatusHistoryRepository history;

  TransactionTemplate tx;
  String writer, reviewer, admin, workspace, project, version, scene;
  ScriptService scripts;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(manager);
    tx.executeWithoutResult(s -> {
      User w = user("WRITER", SystemRole.USER);
      User r = user("REVIEWER", SystemRole.USER);
      User a = user("ADMIN", SystemRole.ADMIN);
      writer = w.getId();
      reviewer = r.getId();
      admin = a.getId();

      Workspace ws = workspaces.saveAndFlush(
          Workspace.builder().name("TEST_ONLY " + UUID.randomUUID()).createdBy(a).build()
      );
      workspace = ws.getId();

      members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(w).role(WorkspaceRole.WRITER).build());
      members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(r).role(WorkspaceRole.REVIEWER).build());

      ClipProject cp = projects.saveAndFlush(
          ClipProject.builder().title("TEST_ONLY STEM Demo").subject(StemSubject.Math).gradeLevel("GRADE_4").owner(w).workspace(ws).build()
      );
      project = cp.getId();

      ScriptVersion sv = versions.saveAndFlush(
          ScriptVersion.builder().project(cp).title("TEST_ONLY Version 1").versionNo(1).createdBy(w).build()
      );
      version = sv.getId();

      Scene sc = scenes.saveAndFlush(
          Scene.builder().scriptVersion(sv).sceneType(SceneType.TITLE_HERO).title("TEST_ONLY Scene").narration("TEST_ONLY narration").props("{}").orderNo(1).build()
      );
      scene = sc.getId();
    });

    AuthService auth = mock(AuthService.class);
    when(auth.findUserOrThrow(anyString())).thenAnswer(i -> users.findById(i.getArgument(0)).orElseThrow());
    scripts = new ScriptService(projects, versions, scenes, new SceneMapper(json, new ScenePropsValidator()), auth, mock(WorkspaceService.class), new StatusHistoryService(history), access);
    ReflectionTestUtils.setField(scripts, "stem", stem);
  }

  User user(String name, SystemRole role) {
    return users.saveAndFlush(User.builder()
        .fullName("TEST_ONLY " + name)
        .username("demo" + UUID.randomUUID().toString().replace("-", "").substring(0, 24))
        .email(UUID.randomUUID() + "@example.invalid")
        .systemRole(role)
        .build());
  }

  private StemDtos.Brief loadBrief(String filename) throws Exception {
    Path p = Path.of("src/test/resources/stem/demo-briefs", filename);
    String content = Files.readString(p);
    return json.readValue(content, StemDtos.Brief.class);
  }

  private void importPackOnce() {
    var req = json.createObjectNode()
        .put("pack", "stem-demo-pack.v1")
        .put("idempotencyKey", "seed-pack-acceptance-" + UUID.randomUUID().toString().substring(0, 12));
    stem.importCatalog(admin, req);
  }

  @Test
  void testImportFirstTimeCreatesAllAndSecondTimeSkipsAll() {
    jdbc.update("delete from stem_assessments");
    jdbc.update("delete from stem_evidence");
    jdbc.update("delete from stem_briefs");
    jdbc.update("update stem_settings set policy_ids='[]'::jsonb, default_policy_id=null");
    jdbc.update("delete from stem_catalog");
    int initialCount = 0;

    // 1. First import
    var req1 = json.createObjectNode()
        .put("pack", "stem-demo-pack.v1")
        .put("idempotencyKey", "first-import-key-01");
    var res1 = stem.importCatalog(admin, req1);
    assertThat(res1.created()).isEqualTo(19);
    assertThat(res1.skipped()).isEqualTo(0);

    int countAfter1 = jdbc.queryForObject("select count(*) from stem_catalog", Integer.class);
    assertThat(countAfter1).isEqualTo(initialCount + 19);

    // Verify audit
    int audits = jdbc.queryForObject("select count(*) from stem_audit where event='CATALOG_IMPORT'", Integer.class);
    assertThat(audits).isGreaterThanOrEqualTo(1);

    // 2. Second import (identical) -> skips all
    var req2 = json.createObjectNode()
        .put("pack", "stem-demo-pack.v1")
        .put("idempotencyKey", "second-import-key-02");
    var res2 = stem.importCatalog(admin, req2);
    assertThat(res2.created()).isEqualTo(0);
    assertThat(res2.skipped()).isEqualTo(19);

    int countAfter2 = jdbc.queryForObject("select count(*) from stem_catalog", Integer.class);
    assertThat(countAfter2).isEqualTo(countAfter1);
  }

  @Test
  void testImportConflictThrows409AndKeepsDbUnchanged() throws Exception {
    importPackOnce();
    int countBefore = jdbc.queryForObject("select count(*) from stem_catalog", Integer.class);

    // Read demo pack and mutate one item
    InputStream is = getClass().getResourceAsStream("/stem/demo-pack/stem-demo-pack.v1.json");
    JsonNode packNode = json.readTree(is);
    var itemsNode = (com.fasterxml.jackson.databind.node.ArrayNode) packNode.get("items");
    var firstItem = (com.fasterxml.jackson.databind.node.ObjectNode) itemsNode.get(0);
    var modifiedData = json.createObjectNode().put("tampered", true);
    firstItem.set("data", modifiedData);

    var conflictReq = json.createObjectNode()
        .put("schemaVersion", "stem.v1")
        .put("idempotencyKey", "conflict-key-001");
    conflictReq.set("items", itemsNode);

    assertThatThrownBy(() -> stem.importCatalog(admin, conflictReq))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(409);
        });

    int countAfter = jdbc.queryForObject("select count(*) from stem_catalog", Integer.class);
    assertThat(countAfter).isEqualTo(countBefore);
  }

  @Test
  void testImportWhenStemDisabledReturns404() throws Exception {
    var emptyMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup().build();
    emptyMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/stem/catalog/import")
            .contentType("application/json")
            .content("{\"pack\":\"stem-demo-pack.v1\",\"idempotencyKey\":\"test-key-001\"}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
  }

  @Test
  void testControllerEndpointImport() throws Exception {
    importPackOnce();
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new StemController(stem, validator))
        .setControllerAdvice(new com.stemotion.core.exception.GlobalExceptionHandler())
        .build();

    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/stem/catalog/import")
            .principal(() -> writer)
            .contentType("application/json")
            .content("{\"pack\":\"stem-demo-pack.v1\",\"idempotencyKey\":\"mvc-import-key-01\"}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());

    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/stem/catalog/import")
            .principal(() -> admin)
            .contentType("application/json")
            .content("{\"pack\":\"stem-demo-pack.v1\",\"idempotencyKey\":\"mvc-import-key-02\"}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.created").isNumber())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.skipped").isNumber());
  }

  @Test
  void testImportNonAdminThrows403() {
    var req = json.createObjectNode()
        .put("pack", "stem-demo-pack.v1")
        .put("idempotencyKey", "non-admin-key-001");

    assertThatThrownBy(() -> stem.importCatalog(writer, req))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(403);
        });
  }

  @Test
  void AT16_PRIMARY_VALID() throws Exception {
    importPackOnce();
    String primaryPolicy = "00000000-0000-0000-0000-000000000005";

    // Set workspace to STRICT mode with primary policy
    stem.saveSettings(workspace, admin, new StemDtos.Settings(
        0, "STRICT", List.of(primaryPolicy), primaryPolicy, "primary-strict-set-01", null
    ));

    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "at16-p-valid-create"));
    StemDtos.Brief brief = loadBrief("primary-valid.json");
    stem.save(project, version, writer, new StemDtos.Save("stem.v1", 0, 0, "at16-p-valid-save", brief, List.of()));

    // Submit for review in STRICT mode -> must succeed
    var response = tx.execute(s -> scripts.submitForReview(project, writer));
    assertThat(response).isNotNull();
    assertThat(response.scriptStatus()).isEqualTo(ScriptStatus.IN_REVIEW);
  }

  @Test
  void AT16_PRIMARY_INVALID() throws Exception {
    importPackOnce();
    String primaryPolicy = "00000000-0000-0000-0000-000000000005";

    stem.saveSettings(workspace, admin, new StemDtos.Settings(
        0, "STRICT", List.of(primaryPolicy), primaryPolicy, "primary-strict-set-02", null
    ));

    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "at16-p-invalid-create"));
    StemDtos.Brief brief = loadBrief("primary-invalid.json");
    stem.save(project, version, writer, new StemDtos.Save("stem.v1", 0, 0, "at16-p-invalid-save", brief, List.of()));

    // Submit for review in STRICT mode -> must fail with TECH-LESSON-OPENING
    assertThatThrownBy(() -> tx.execute(s -> scripts.submitForReview(project, writer)))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(400);
          assertThat(e.getCode()).isEqualTo("STEM_VALIDATION_FAILED");
          @SuppressWarnings("unchecked")
          var findings = (List<StemDtos.Finding>) e.getDetails().get("findings");
          assertThat(findings).anyMatch(f -> "TECH-LESSON-OPENING".equals(f.ruleId()) && "ERROR".equals(f.severity()));
        });
  }

  @Test
  void AT16_LOWER_SECONDARY_VALID() throws Exception {
    importPackOnce();
    String policy = "00000000-0000-0000-0000-000000000010";

    stem.saveSettings(workspace, admin, new StemDtos.Settings(
        0, "STRICT", List.of(policy), policy, "lower-strict-set-01", null
    ));

    // Update project gradeLevel to match GRADE_8
    ClipProject cp = projects.findById(project).orElseThrow();
    cp.setGradeLevel("GRADE_8");
    projects.saveAndFlush(cp);

    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "at16-ls-valid-create"));
    StemDtos.Brief brief = loadBrief("lower-secondary-valid.json");
    stem.save(project, version, writer, new StemDtos.Save("stem.v1", 0, 0, "at16-ls-valid-save", brief, List.of()));

    var response = tx.execute(s -> scripts.submitForReview(project, writer));
    assertThat(response).isNotNull();
    assertThat(response.scriptStatus()).isEqualTo(ScriptStatus.IN_REVIEW);
  }

  @Test
  void AT16_LOWER_SECONDARY_INVALID() throws Exception {
    importPackOnce();
    String policy = "00000000-0000-0000-0000-000000000010";

    stem.saveSettings(workspace, admin, new StemDtos.Settings(
        0, "STRICT", List.of(policy), policy, "lower-strict-set-02", null
    ));

    ClipProject cp = projects.findById(project).orElseThrow();
    cp.setGradeLevel("GRADE_8");
    projects.saveAndFlush(cp);

    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "at16-ls-invalid-create"));
    StemDtos.Brief brief = loadBrief("lower-secondary-invalid.json");
    stem.save(project, version, writer, new StemDtos.Save("stem.v1", 0, 0, "at16-ls-invalid-save", brief, List.of()));

    // Submit for review in STRICT mode -> must fail with TECH-LESSON-CLOSING
    assertThatThrownBy(() -> tx.execute(s -> scripts.submitForReview(project, writer)))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(400);
          assertThat(e.getCode()).isEqualTo("STEM_VALIDATION_FAILED");
          @SuppressWarnings("unchecked")
          var findings = (List<StemDtos.Finding>) e.getDetails().get("findings");
          assertThat(findings).anyMatch(f -> "TECH-LESSON-CLOSING".equals(f.ruleId()) && "ERROR".equals(f.severity()));
        });
  }

  @Test
  void AT16_UPPER_SECONDARY_VALID() throws Exception {
    importPackOnce();
    String policy = "00000000-0000-0000-0000-000000000017";

    stem.saveSettings(workspace, admin, new StemDtos.Settings(
        0, "STRICT", List.of(policy), policy, "upper-strict-set-01", null
    ));

    ClipProject cp = projects.findById(project).orElseThrow();
    cp.setGradeLevel("GRADE_11");
    projects.saveAndFlush(cp);

    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "at16-us-valid-create"));
    StemDtos.Brief brief = loadBrief("upper-secondary-valid.json");
    stem.save(project, version, writer, new StemDtos.Save("stem.v1", 0, 0, "at16-us-valid-save", brief, List.of()));

    var response = tx.execute(s -> scripts.submitForReview(project, writer));
    assertThat(response).isNotNull();
    assertThat(response.scriptStatus()).isEqualTo(ScriptStatus.IN_REVIEW);
  }

  @Test
  void AT16_UPPER_SECONDARY_INVALID() throws Exception {
    importPackOnce();
    String policy = "00000000-0000-0000-0000-000000000017";

    stem.saveSettings(workspace, admin, new StemDtos.Settings(
        0, "STRICT", List.of(policy), policy, "upper-strict-set-02", null
    ));

    ClipProject cp = projects.findById(project).orElseThrow();
    cp.setGradeLevel("GRADE_11");
    projects.saveAndFlush(cp);

    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "at16-us-invalid-create"));
    StemDtos.Brief brief = loadBrief("upper-secondary-invalid.json");
    stem.save(project, version, writer, new StemDtos.Save("stem.v1", 0, 0, "at16-us-invalid-save", brief, List.of()));

    // Submit for review in STRICT mode -> must fail with TECH-SCOPE
    assertThatThrownBy(() -> tx.execute(s -> scripts.submitForReview(project, writer)))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(400);
          assertThat(e.getCode()).isEqualTo("STEM_VALIDATION_FAILED");
          @SuppressWarnings("unchecked")
          var findings = (List<StemDtos.Finding>) e.getDetails().get("findings");
          assertThat(findings).anyMatch(f -> "TECH-SCOPE".equals(f.ruleId()) && "ERROR".equals(f.severity()));
        });
  }
}
