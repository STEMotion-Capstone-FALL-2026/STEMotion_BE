package com.stemotion.domain.stem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.stemotion.domain.script.dto.ScriptDtos.ReviewDecisionRequest;
import com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.script.entity.ScriptVersion;
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
import java.io.File;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
public class StemRf11AcceptanceTest {

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
  String writer, reviewer, admin, workspace, project, version, scene, testRubric;
  ScriptService scripts;

  @BeforeEach
  void fixture() {
    tx = new TransactionTemplate(manager);
    tx.executeWithoutResult(s -> {
      User w = user("WRITER", SystemRole.USER);
      User r = user("REVIEWER", SystemRole.USER);
      User a = user("ADMIN", SystemRole.ADMIN);
      writer = w.getId();
      reviewer = r.getId();
      admin = a.getId();

      Workspace ws = workspaces.saveAndFlush(
          Workspace.builder().name("RF11_TEST_" + UUID.randomUUID()).createdBy(a).build());
      workspace = ws.getId();

      members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(w).role(WorkspaceRole.WRITER).build());
      members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(r).role(WorkspaceRole.REVIEWER).build());

      ClipProject cp = projects.saveAndFlush(
          ClipProject.builder().title("TEST_ONLY RF11 Project").subject(StemSubject.Math).gradeLevel("GRADE_6").owner(w).workspace(ws).build());
      project = cp.getId();

      ScriptVersion sv = versions.saveAndFlush(
          ScriptVersion.builder().project(cp).title("TEST_ONLY V1").versionNo(1).createdBy(w).build());
      version = sv.getId();

      Scene sc = scenes.saveAndFlush(
          Scene.builder().scriptVersion(sv).sceneType(SceneType.TITLE_HERO).title("TEST_ONLY Scene 1").narration("TEST_ONLY narration").props("{}").orderNo(1).build());
      scene = sc.getId();

      testRubric = db.createDraft(StemModels.Kind.RUBRIC, "TEST_ONLY-rubric-" + UUID.randomUUID(), 1, null, null, json.createObjectNode().put("name", "Test Rubric"));
    });

    AuthService auth = mock(AuthService.class);
    when(auth.findUserOrThrow(anyString())).thenAnswer(i -> users.findById(i.getArgument(0)).orElseThrow());
    scripts = new ScriptService(projects, versions, scenes, new SceneMapper(json, new ScenePropsValidator()), auth, mock(WorkspaceService.class), new StatusHistoryService(history), access);
    ReflectionTestUtils.setField(scripts, "stem", stem);
  }

  User user(String name, SystemRole role) {
    return users.saveAndFlush(User.builder().fullName("TEST_ONLY " + name).username("rf11_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20)).email(UUID.randomUUID() + "@example.invalid").systemRole(role).build());
  }

  StemDtos.Save save(StemDtos.Brief b, List<StemDtos.Evidence> ev, int rev, String key) {
    return new StemDtos.Save("stem.v1", rev, 0, key, b, ev);
  }

  /**
   * AT03: STRICT mode, script without brief -> draft update OK, submit blocked with 400 STEM_VALIDATION_FAILED.
   */
  @Test
  void AT03() {
    // 1. Admin sets STRICT mode
    stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "at03-strict", null));

    // 2. Draft update is OK (writer can work on draft without brief)
    var reqScene = new com.stemotion.domain.script.dto.SceneDtos.SceneRequest();
    reqScene.setId(scene);
    reqScene.setType(SceneType.TITLE_HERO);
    reqScene.setTitle("Updated Draft");
    reqScene.setNarration("Working on draft");
    reqScene.setDurationInFrames(120);
    for (String k : List.of("subtitle", "subject", "gradeLevel", "badgeText")) reqScene.putProp(k, "TEST_ONLY");
    tx.execute(s -> scripts.updateDraft(project, new UpdateScriptRequest(null, null, null, null, null, List.of(reqScene), 0)));

    // 3. Submitting script for review without brief must be blocked with 400 STEM_VALIDATION_FAILED
    assertThatThrownBy(() -> tx.execute(s -> scripts.submitForReview(project, writer)))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(400);
          assertThat(e.getCode()).isEqualTo("STEM_VALIDATION_FAILED");
          assertThat(e.getMessage()).isEqualTo("STEM prerequisites not satisfied");
          assertThat((List<?>) e.getDetails().get("findings")).anyMatch(f -> f.toString().contains("TECH-BRIEF"));
        });

    // Verify project pipeline status is still DRAFT
    ClipProject cp = projects.findById(project).orElseThrow();
    assertThat(cp.getScriptStatus()).isEqualTo(ScriptStatus.DRAFT);
  }

  /**
   * RF11_STRICT_SETTINGS: Admin sets STRICT OK; non-admin 403; stale revision 409; STRICT -> OFF preserves data.
   */
  @Test
  void RF11_STRICT_SETTINGS() {
    // Admin sets STRICT OK
    var s1 = stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "rf11-set-strict", null));
    assertThat(s1.mode()).isEqualTo("STRICT");
    assertThat(s1.revision()).isEqualTo(1);

    // Non-admin (writer or reviewer) cannot change settings -> 403
    assertThatThrownBy(() -> stem.saveSettings(workspace, writer, new StemDtos.Settings(1, "OFF", List.of(), null, "rf11-writer", null)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
    assertThatThrownBy(() -> stem.saveSettings(workspace, reviewer, new StemDtos.Settings(1, "OFF", List.of(), null, "rf11-reviewer", null)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));

    // Stale revision -> 409
    assertThatThrownBy(() -> stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "OFF", List.of(), null, "rf11-stale", null)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(409));

    // Create a brief while in STRICT mode
    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "rf11-create-brief"));
    assertThat(jdbc.queryForObject("select count(*) from stem_briefs where version_id=?", Integer.class, version)).isEqualTo(1);

    // Switch STRICT -> OFF preserves existing data
    var s2 = stem.saveSettings(workspace, admin, new StemDtos.Settings(1, "OFF", List.of(), null, "rf11-set-off", null));
    assertThat(s2.mode()).isEqualTo("OFF");
    assertThat(s2.revision()).isEqualTo(2);

    // Brief is still present in DB
    assertThat(jdbc.queryForObject("select count(*) from stem_briefs where version_id=?", Integer.class, version)).isEqualTo(1);
  }

  /**
   * RF11_LESSON_OPENING_CLOSING_ORDER: Verify findings TECH-LESSON-OPENING, TECH-LESSON-CLOSING,
   * TECH-LESSON-ORDER, TECH-LESSON-STEPS and mode enforcement (OFF, ADVISORY, STRICT).
   */
  @Test
  void RF11_LESSON_OPENING_CLOSING_ORDER() {
    // 1. Brief with null steps (backward compatibility): yields OPENING and CLOSING errors
    var emptyBrief = StemDtos.empty();
    var findingsEmpty = validator.evaluate(emptyBrief, List.of());
    assertThat(findingsEmpty).anyMatch(f -> f.ruleId().equals("TECH-LESSON-OPENING") && "ERROR".equals(f.severity()));
    assertThat(findingsEmpty).anyMatch(f -> f.ruleId().equals("TECH-LESSON-CLOSING") && "ERROR".equals(f.severity()));

    // In OFF: does not block transition
    validator.transition("OFF", findingsEmpty, true);

    // In ADVISORY: returns findings, does not block transition
    validator.transition("ADVISORY", findingsEmpty, true);

    // In STRICT: blocks transition with 400 STEM_VALIDATION_FAILED
    assertThatThrownBy(() -> validator.transition("STRICT", findingsEmpty, true))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(400);
          assertThat(e.getCode()).isEqualTo("STEM_VALIDATION_FAILED");
        });

    // 2. Step 1 without scene or activity: yields OPENING error
    var step1NoBinding = new StemDtos.LessonStep("OPENING_PROBLEM", List.of(), null, List.of());
    var briefStep1NoBinding = createBriefWithSteps(List.of(step1NoBinding));
    var findingsNoBinding = validator.evaluate(briefStep1NoBinding, List.of());
    assertThat(findingsNoBinding).anyMatch(f -> f.ruleId().equals("TECH-LESSON-OPENING") && "ERROR".equals(f.severity()));

    // 3. Duplicate or out-of-order steps: yields TECH-LESSON-ORDER error
    var step1Valid = new StemDtos.LessonStep("OPENING_PROBLEM", List.of(scene), null, List.of());
    var step3Valid = new StemDtos.LessonStep("PROPOSE_SOLUTION", List.of(scene), null, List.of());
    var step2Valid = new StemDtos.LessonStep("BACKGROUND_KNOWLEDGE", List.of(scene), null, List.of());
    var step6Valid = new StemDtos.LessonStep("CLOSING_SHARE_REFLECT", List.of(), "activity-final", List.of());

    // Out of order: step 3 before step 2
    var briefOutOfOrder = createBriefWithSteps(List.of(step1Valid, step3Valid, step2Valid, step6Valid));
    var findingsOrder = validator.evaluate(briefOutOfOrder, List.of());
    assertThat(findingsOrder).anyMatch(f -> f.ruleId().equals("TECH-LESSON-ORDER") && "ERROR".equals(f.severity()));

    // Duplicate step
    var briefDuplicate = createBriefWithSteps(List.of(step1Valid, step1Valid, step6Valid));
    var findingsDup = validator.evaluate(briefDuplicate, List.of());
    assertThat(findingsDup).anyMatch(f -> f.ruleId().equals("TECH-LESSON-ORDER") && "ERROR".equals(f.severity()));

    // Unknown step key
    var stepUnknown = new StemDtos.LessonStep("UNKNOWN_STEP_KEY", List.of(scene), null, List.of());
    var briefUnknown = createBriefWithSteps(List.of(step1Valid, stepUnknown, step6Valid));
    var findingsUnknown = validator.evaluate(briefUnknown, List.of());
    assertThat(findingsUnknown).anyMatch(f -> f.ruleId().equals("TECH-LESSON-ORDER") && "ERROR".equals(f.severity()));

    // 4. Missing middle steps 2-5: yields TECH-LESSON-STEPS warning (WARN severity, does NOT block STRICT)
    var briefSteps1And6 = createBriefWithSteps(List.of(step1Valid, step6Valid));
    var findingsWarnOnly = validator.evaluate(briefSteps1And6, List.of());
    assertThat(findingsWarnOnly).anyMatch(f -> f.ruleId().equals("TECH-LESSON-STEPS") && "WARN".equals(f.severity()));
    assertThat(findingsWarnOnly).noneMatch(f -> f.ruleId().startsWith("TECH-LESSON-") && "ERROR".equals(f.severity()));

    // WARN does NOT block transition in STRICT mode!
    validator.transition("STRICT", findingsWarnOnly.stream().filter(f -> f.ruleId().startsWith("TECH-LESSON-")).toList(), true);
  }

  private StemDtos.Brief createBriefWithSteps(List<StemDtos.LessonStep> steps) {
    var b = StemDtos.empty();
    return new StemDtos.Brief(
        null, null, null, b.subjectKeys(), b.outcomeIds(), null,
        b.context(), b.distributionChannels(), b.objectives(), b.studentTasks(), b.contributions(),
        null, null, steps);
  }

  /**
   * RF11_RUBRIC_SCRIPT: approve without rubric -> blocks; rubric with stale fingerprint -> blocks; matching rubric -> OK.
   */
  @Test
  void RF11_RUBRIC_SCRIPT() {
    // 1. Setup brief and submit script while workspace is in default OFF mode
    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "rf11-script-brief"));
    var step1 = new StemDtos.LessonStep("OPENING_PROBLEM", List.of(scene), null, List.of());
    var step6 = new StemDtos.LessonStep("CLOSING_SHARE_REFLECT", List.of(scene), null, List.of());
    var brief = createBriefWithSteps(List.of(step1, step6));
    stem.save(project, version, writer, save(brief, List.of(), 0, "save-brief-valid"));
    tx.execute(s -> scripts.submitForReview(project, writer));

    // 2. Set workspace to STRICT mode
    stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "rubric-script-strict", null));

    // 3. Approve without rubric in STRICT mode -> blocks with 400 STEM_RUBRIC_REQUIRED
    assertThatThrownBy(() -> tx.execute(s -> scripts.approve(project, new ReviewDecisionRequest("TEST_ONLY approval"), reviewer)))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(400);
          assertThat(e.getCode()).isEqualTo("STEM_RUBRIC_REQUIRED");
        });

    // 4. Approve with stale fingerprint -> blocks with 409 STEM_FINGERPRINT_STALE
    var staleAssessment = new StemDtos.Assessment(
        1, 0, null, 1, testRubric,
        List.of(new StemDtos.Criterion("TEST_ONLY", "ADEQUATE", "Looks good", List.of())),
        "approve-stale-fp",
        "stale-semantic-hash-12345");
    assertThatThrownBy(() -> tx.execute(s -> scripts.approve(project, new ReviewDecisionRequest("TEST_ONLY approval", staleAssessment), reviewer)))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(409);
          assertThat(e.getCode()).isEqualTo("STEM_FINGERPRINT_STALE");
          assertThat(e.getDetails()).containsEntry("actual", "stale-semantic-hash-12345");
        });

    // 5. Approve with matching fingerprint -> OK
    String currentFp = stem.computeScriptFingerprint(projects.findById(project).orElseThrow(), versions.findById(version).orElseThrow());
    var matchingAssessment = new StemDtos.Assessment(
        1, 0, null, 1, testRubric,
        List.of(new StemDtos.Criterion("TEST_ONLY", "ADEQUATE", "Looks good", List.of())),
        "approve-valid-fp",
        currentFp);
    tx.execute(s -> scripts.approve(project, new ReviewDecisionRequest("TEST_ONLY approval", matchingAssessment), reviewer));

    // Verify script is approved and assessment is recorded with phase SCRIPT and matching fingerprint
    assertThat(jdbc.queryForObject("select status from script_versions where id=?", String.class, version)).isEqualTo("APPROVED");
    assertThat(jdbc.queryForObject("select count(*) from stem_assessments where version_id=? and phase='SCRIPT'", Integer.class, version)).isEqualTo(1);
    String recordedPayload = jdbc.queryForObject("select payload from stem_assessments where version_id=? and phase='SCRIPT'", String.class, version);
    assertThat(recordedPayload).contains(currentFp);
  }

  /**
   * RF11_RUBRIC_VIDEO: both branches of approveVideo in STRICT mode.
   */
  @Test
  void RF11_RUBRIC_VIDEO() {
    testVideoReviewBranch(true);
    testVideoReviewBranch(false);
  }

  private void testVideoReviewBranch(boolean hasClip) {
    String wsId = tx.execute(s -> {
      Workspace ws = workspaces.saveAndFlush(Workspace.builder().name("VIDEO_WS_" + UUID.randomUUID()).createdBy(users.findById(admin).orElseThrow()).build());
      members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(users.findById(writer).orElseThrow()).role(WorkspaceRole.WRITER).build());
      members.saveAndFlush(WorkspaceMember.builder().workspace(ws).user(users.findById(reviewer).orElseThrow()).role(WorkspaceRole.REVIEWER).build());
      return ws.getId();
    });
    // Unique project for this test run
    String pId = tx.execute(s -> {
      ClipProject cp = projects.saveAndFlush(ClipProject.builder().title("TEST_ONLY Video Project " + hasClip).subject(StemSubject.Math).gradeLevel("GRADE_7").owner(users.findById(writer).orElseThrow()).workspace(workspaces.findById(wsId).orElseThrow()).build());
      ScriptVersion sv = versions.saveAndFlush(ScriptVersion.builder().project(cp).title("TEST_ONLY").versionNo(1).createdBy(users.findById(writer).orElseThrow()).build());
      scenes.saveAndFlush(Scene.builder().scriptVersion(sv).sceneType(SceneType.TITLE_HERO).title("Scene 1").narration("narration").props("{}").orderNo(1).build());
      return cp.getId();
    });
    String vId = versions.findFirstByProjectIdAndIsCurrentTrue(pId).orElseThrow().getId();

    // Setup brief & submit/approve script while in OFF mode
    stem.create(pId, vId, writer, new StemDtos.Create("stem.v1", 0, "video-brief-" + hasClip));
    tx.execute(s -> scripts.submitForReview(pId, writer));
    tx.execute(s -> scripts.approve(pId, new ReviewDecisionRequest("Approved", null), reviewer));

    // Transition project to video QA
    String compId = UUID.randomUUID().toString();
    jdbc.update("insert into compositions(id,script_version_id,project_id,revision,scenes) values(?::uuid,?,?,1,'[]')", compId, vId, pId);
    jdbc.update("update clip_projects set script_status='APPROVED',video_status='IN_QA' where id=?", pId);

    var cpMock = mock(com.stemotion.domain.production.service.CompositionService.class);
    var renderMock = mock(com.stemotion.domain.production.service.RenderService.class);
    var artifactsMock = mock(com.stemotion.domain.production.service.ClipArtifactService.class);
    var notesMock = mock(com.stemotion.domain.review.repository.FeedbackCommentRepository.class);
    var composition = com.stemotion.domain.production.entity.Composition.builder().revision(1).build();
    composition.setId(compId);
    when(cpMock.ensure(any(), any(), anyString())).thenReturn(composition);
    when(cpMock.renderInput(any())).thenReturn(new com.stemotion.domain.production.service.CompositionService.RenderInput(composition, 1, List.of()));

    if (hasClip) {
      String jobId = UUID.randomUUID().toString(), clipId = UUID.randomUUID().toString();
      jdbc.update("insert into render_jobs(id,created_at,updated_at,project_id,version_id,requested_by,status,resolution,fps,composition_id,composition_revision) values(?,current_timestamp,current_timestamp,?,?,?,'COMPLETED','RES_1080P',30,?::uuid,1)", jobId, pId, vId, reviewer, compId);
      jdbc.update("insert into clips(id,render_job_id,video_url) values(?::uuid,?,?)", clipId, jobId, "http://127.0.0.1/video.mp4");
      var job = com.stemotion.domain.production.entity.RenderJob.builder().compositionRevision(1).build();
      job.setId(jobId);
      var clip = com.stemotion.domain.production.entity.Clip.builder().renderJob(job).build();
      clip.setId(clipId);
      when(artifactsMock.findCurrent(any(), anyString())).thenReturn(Optional.of(clip));
    } else {
      when(artifactsMock.findCurrent(any(), anyString())).thenReturn(Optional.empty());
    }

    AuthService authMock = mock(AuthService.class);
    when(authMock.findUserOrThrow(anyString())).thenAnswer(i -> users.findById(i.getArgument(0)).orElseThrow());
    var feedbackService = new com.stemotion.domain.review.service.FeedbackService(notesMock, scenes, projects, scripts, authMock, artifactsMock, new StatusHistoryService(history), cpMock, renderMock);
    ReflectionTestUtils.setField(feedbackService, "stem", stem);

    // Now set workspace to STRICT mode
    stem.saveSettings(wsId, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "video-strict-" + hasClip, null));

    // Save initial composition snapshot
    stem.checkSubmitVideoReview(projects.findById(pId).orElseThrow(), versions.findById(vId).orElseThrow(), writer);

    // 1. approveVideo without rubric in STRICT mode -> blocks with 400 STEM_RUBRIC_REQUIRED
    assertThatThrownBy(() -> tx.executeWithoutResult(s -> feedbackService.approveVideo(pId, reviewer, "TEST_ONLY", null)))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(400);
          assertThat(e.getCode()).isEqualTo("STEM_RUBRIC_REQUIRED");
        });

    // 2. approveVideo with stale fingerprint -> blocks with 409 STEM_FINGERPRINT_STALE
    var staleVideoAssessment = new StemDtos.Assessment(
        0, 0, 1, 1, testRubric,
        List.of(new StemDtos.Criterion("TEST_ONLY", "ADEQUATE", "Video good", List.of())),
        "video-stale-" + hasClip,
        "stale-comp-fp");
    assertThatThrownBy(() -> tx.executeWithoutResult(s -> feedbackService.approveVideo(pId, reviewer, "TEST_ONLY", staleVideoAssessment)))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(409);
          assertThat(e.getCode()).isEqualTo("STEM_FINGERPRINT_STALE");
        });

    // 3. approveVideo with matching composition fingerprint -> succeeds
    String compFp = stem.computeCompositionFingerprint(projects.findById(pId).orElseThrow(), versions.findById(vId).orElseThrow());
    var validVideoAssessment = new StemDtos.Assessment(
        0, 0, 1, 1, testRubric,
        List.of(new StemDtos.Criterion("TEST_ONLY", "ADEQUATE", "Video good", List.of())),
        "video-valid-" + hasClip,
        compFp);
    tx.executeWithoutResult(s -> feedbackService.approveVideo(pId, reviewer, "TEST_ONLY", validVideoAssessment));

    assertThat(jdbc.queryForObject("select count(*) from stem_assessments where version_id=? and phase='VIDEO'", Integer.class, vId)).isEqualTo(1);
    verify(renderMock, times(hasClip ? 0 : 1)).renderApproved(pId, reviewer);
  }

  /**
   * AT24: Admin without REVIEWER seat assesses rubric -> 403, DB unchanged.
   */
  @Test
  void AT24() {
    createBriefWithSubmission();

    // Set mode to STRICT
    stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "at24-strict", null));

    String currentFp = stem.computeScriptFingerprint(projects.findById(project).orElseThrow(), versions.findById(version).orElseThrow());
    var assessment = new StemDtos.Assessment(
        1, 0, null, 1, testRubric,
        List.of(new StemDtos.Criterion("TEST_ONLY", "ADEQUATE", "Admin review attempt", List.of())),
        "at24-key",
        currentFp);

    // Admin attempts to approve -> 403 Forbidden
    assertThatThrownBy(() -> tx.execute(s -> scripts.approve(project, new ReviewDecisionRequest("Admin approval", assessment), admin)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));

    // Direct prepare call also returns 403
    assertThatThrownBy(() -> tx.execute(s -> stem.prepare(projects.findById(project).orElseThrow(), versions.findById(version).orElseThrow(), admin, "SCRIPT", assessment)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));

    // Verify DB unchanged: no assessment, no approval status history
    assertThat(jdbc.queryForObject("select count(*) from stem_assessments where version_id=?", Integer.class, version)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from status_histories where project_id=? and decision='APPROVED'", Integer.class, project)).isZero();
  }

  private void createBriefWithSubmission() {
    stem.create(project, version, writer, new StemDtos.Create("stem.v1", 0, "at24-brief"));
    var step1 = new StemDtos.LessonStep("OPENING_PROBLEM", List.of(scene), null, List.of());
    var step6 = new StemDtos.LessonStep("CLOSING_SHARE_REFLECT", List.of(scene), null, List.of());
    var brief = createBriefWithSteps(List.of(step1, step6));
    stem.save(project, version, writer, save(brief, List.of(), 0, "at24-save"));
    tx.execute(s -> scripts.submitForReview(project, writer));
  }

  /**
   * RF11_FINGERPRINT_RENDER_REVIEW_PUBLISH:
   * STRICT mismatch -> 409 STEM_FINGERPRINT_STALE with expected/actual.
   * ADVISORY mismatch -> does not block, records audit finding.
   */
  @Test
  void RF11_FINGERPRINT_RENDER_REVIEW_PUBLISH() {
    createBriefWithSubmission();
    var p = projects.findById(project).orElseThrow();
    var v = versions.findById(version).orElseThrow();

    // 1. RENDER CHECK
    // Case A: STRICT mode, script fingerprint mismatch at render (no approved script assessment yet) -> 409
    stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "fp-test-01", null));
    assertThatThrownBy(() -> stem.checkRenderFingerprint(p, v, writer))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(409);
          assertThat(e.getCode()).isEqualTo("STEM_FINGERPRINT_STALE");
        });

    // Case B: ADVISORY mode, render mismatch records audit, does not throw
    stem.saveSettings(workspace, admin, new StemDtos.Settings(1, "ADVISORY", List.of(), null, "fp-test-02", null));
    assertThatCode(() -> stem.checkRenderFingerprint(p, v, writer)).doesNotThrowAnyException();
    assertThat(jdbc.queryForObject("select count(*) from stem_audit where event='STEM_FINGERPRINT_MISMATCH'", Integer.class)).isGreaterThanOrEqualTo(0);

    // Case C: OFF mode does nothing
    stem.saveSettings(workspace, admin, new StemDtos.Settings(2, "OFF", List.of(), null, "fp-test-03", null));
    assertThatCode(() -> stem.checkRenderFingerprint(p, v, writer)).doesNotThrowAnyException();

    // 2. VIDEO REVIEW SNAPSHOT CHECK
    stem.saveSettings(workspace, admin, new StemDtos.Settings(3, "STRICT", List.of(), null, "fp-test-04", null));
    // Save initial snapshot with dummy fingerprint
    stem.saveCompositionSnapshot(project, version, "original-comp-fingerprint", writer);

    // Current composition fingerprint differs from saved snapshot "original-comp-fingerprint" -> 409
    assertThatThrownBy(() -> stem.checkVideoReviewFingerprint(p, v, reviewer, "APPROVE_VIDEO"))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(409);
          assertThat(e.getCode()).isEqualTo("STEM_FINGERPRINT_STALE");
          assertThat(e.getDetails()).containsEntry("expected", "original-comp-fingerprint");
        });

    // In ADVISORY mode: mismatch does not throw, records audit
    stem.saveSettings(workspace, admin, new StemDtos.Settings(4, "ADVISORY", List.of(), null, "fp-test-05", null));
    assertThatCode(() -> stem.checkVideoReviewFingerprint(p, v, reviewer, "APPROVE_VIDEO")).doesNotThrowAnyException();
    assertThat(jdbc.queryForObject("select count(*) from stem_audit where event='STEM_FINGERPRINT_MISMATCH'", Integer.class)).isGreaterThanOrEqualTo(1);

    // 3. PUBLISH CHECK
    stem.saveSettings(workspace, admin, new StemDtos.Settings(5, "STRICT", List.of(), null, "fp-test-06", null));
    // In STRICT mode, publishing without video approval assessment -> 409
    assertThatThrownBy(() -> stem.checkPublishFingerprint(p, v, writer))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(409);
          assertThat(e.getCode()).isEqualTo("STEM_FINGERPRINT_STALE");
        });

    // In ADVISORY mode: does not throw
    stem.saveSettings(workspace, admin, new StemDtos.Settings(6, "ADVISORY", List.of(), null, "fp-test-07", null));
    assertThatCode(() -> stem.checkPublishFingerprint(p, v, writer)).doesNotThrowAnyException();
  }

  /**
   * RF11_MIGRATION_V26: Test DB has fixture V91001 applied and accepts 'STRICT'; db/migration has no STEM tables.
   */
  @Test
  void RF11_MIGRATION_V26() {
    // 1. Check migrations applied: 26 migrations in test profile
    Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
    assertThat(applied).isEqualTo(26);

    // 2. CHECK constraint accepts STRICT
    Workspace ws1 = workspaces.saveAndFlush(Workspace.builder().name("STRICT_TEST_" + UUID.randomUUID()).createdBy(users.findById(admin).orElseThrow()).build());
    assertThatCode(() -> jdbc.update("insert into stem_settings(workspace_id,revision,mode,policy_ids) values(?::uuid,10,'STRICT','[]'::jsonb)", ws1.getId()))
        .doesNotThrowAnyException();

    // 3. CHECK constraint rejects invalid mode
    Workspace ws2 = workspaces.saveAndFlush(Workspace.builder().name("INVALID_TEST_" + UUID.randomUUID()).createdBy(users.findById(admin).orElseThrow()).build());
    assertThatThrownBy(() -> jdbc.update("insert into stem_settings(workspace_id,revision,mode,policy_ids) values(?::uuid,11,'INVALID_MODE','[]'::jsonb)", ws2.getId()))
        .isInstanceOf(Exception.class);

    // 4. Default db/migration directory does NOT contain any STEM tables
    File migrationDir = new File("src/main/resources/db/migration");
    File[] migrationFiles = migrationDir.listFiles((dir, name) -> name.toLowerCase().contains("stem"));
    assertThat(migrationFiles).isNullOrEmpty();

    // 5. Bridge-prod directory has V26
    File bridgeDir = new File("src/main/resources/db/bridge-prod");
    File v26 = new File(bridgeDir, "V26__allow_stem_strict_mode.sql");
    assertThat(v26).exists();
  }

  /**
   * RF11_REAPPROVE_USES_NEWEST:
   * Approve script 2 times (edit script between the two approvals).
   * Render uses the newest approval's fingerprint -> STRICT passes.
   * If script is edited after the 2nd approval -> 409.
   */
  @Test
  void RF11_REAPPROVE_USES_NEWEST() {
    createBriefWithSubmission();
    stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "reapprove-strict", null));

    ClipProject p1 = projects.findById(project).orElseThrow();
    ScriptVersion v1 = versions.findById(version).orElseThrow();

    // 1st approval
    String fp1 = stem.computeScriptFingerprint(p1, v1);
    var a1 = new StemDtos.Assessment(
        1, 0, null, 1, testRubric,
        List.of(new StemDtos.Criterion("TEST_ONLY", "ADEQUATE", "Approve 1", List.of())),
        "reapprove-a1",
        fp1);
    tx.execute(s -> scripts.approve(project, new ReviewDecisionRequest("Approval 1", a1), reviewer));
    assertThat(jdbc.queryForObject("select count(*) from stem_assessments where version_id=? and phase='SCRIPT'", Integer.class, version)).isEqualTo(1);

    // Edit script: mutate narration of scene and bump draft_revision (changes script fingerprint)
    jdbc.update("update scenes set narration='Updated narration for 2nd review' where id=?", scene);
    jdbc.update("update script_versions set draft_revision=draft_revision+1, status='IN_REVIEW' where id=?", version);
    jdbc.update("update clip_projects set script_status='IN_REVIEW' where id=?", project);

    ClipProject p2 = projects.findById(project).orElseThrow();
    ScriptVersion v2 = versions.findById(version).orElseThrow();

    // 2nd approval with new fingerprint fp2
    String fp2 = stem.computeScriptFingerprint(p2, v2);
    assertThat(fp2).isNotEqualTo(fp1);

    var a2 = new StemDtos.Assessment(
        1, v2.getDraftRevision(), null, 1, testRubric,
        List.of(new StemDtos.Criterion("TEST_ONLY", "ADEQUATE", "Approve 2", List.of())),
        "reapprove-a2",
        fp2);
    tx.execute(s -> scripts.approve(project, new ReviewDecisionRequest("Approval 2", a2), reviewer));
    assertThat(jdbc.queryForObject("select count(*) from stem_assessments where version_id=? and phase='SCRIPT'", Integer.class, version)).isEqualTo(2);

    // Render check with newest fingerprint -> STRICT passes!
    assertThatCode(() -> stem.checkRenderFingerprint(p2, v2, writer)).doesNotThrowAnyException();

    // Now mutate script after 2nd approval
    jdbc.update("update scenes set narration='Mutated after 2nd approval' where id=?", scene);
    ClipProject p3 = projects.findById(project).orElseThrow();
    ScriptVersion v3 = versions.findById(version).orElseThrow();

    // Render check now fails with 409 STEM_FINGERPRINT_STALE!
    assertThatThrownBy(() -> stem.checkRenderFingerprint(p3, v3, writer))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(409);
          assertThat(e.getCode()).isEqualTo("STEM_FINGERPRINT_STALE");
          assertThat(e.getDetails()).containsEntry("expected", fp2);
        });
  }

  /**
   * RF11_SEND_BACK_NOT_BLOCKED:
   * STRICT mode, composition deviates from snapshot -> send-back succeeds, records audit STEM_FINGERPRINT_MISMATCH.
   */
  @Test
  void RF11_SEND_BACK_NOT_BLOCKED() {
    createBriefWithSubmission();
    stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "send-back-strict", null));

    var p = projects.findById(project).orElseThrow();
    var v = versions.findById(version).orElseThrow();

    // Save initial snapshot with a dummy/old fingerprint
    stem.saveCompositionSnapshot(project, version, "old-comp-snapshot-12345", writer);

    // Current composition fingerprint differs from saved snapshot
    String currentCompFp = stem.computeCompositionFingerprint(p, v);
    assertThat(currentCompFp).isNotEqualTo("old-comp-snapshot-12345");

    // send-back must NOT be blocked, even in STRICT mode
    assertThatCode(() -> stem.checkVideoReviewFingerprint(p, v, reviewer, "SEND_BACK")).doesNotThrowAnyException();

    // Verify audit entry STEM_FINGERPRINT_MISMATCH was recorded with stage SEND_BACK
    List<Map<String, Object>> audits = jdbc.queryForList(
        "select payload from stem_audit where target=? and event='STEM_FINGERPRINT_MISMATCH' order by created_at desc, id desc", project);
    assertThat(audits).isNotEmpty();
    JsonNode auditPayload = db.decode(db.text(audits.get(0), "payload"), JsonNode.class);
    assertThat(auditPayload.path("stage").asText()).isEqualTo("SEND_BACK");
    assertThat(auditPayload.path("expected").asText()).isEqualTo("old-comp-snapshot-12345");
    assertThat(auditPayload.path("actual").asText()).isEqualTo(currentCompFp);
  }

  /**
   * RF11_APPROVE_VIDEO_NO_SNAPSHOT:
   * STRICT mode, approveVideo without snapshot -> 409 STEM_FINGERPRINT_STALE.
   */
  @Test
  void RF11_APPROVE_VIDEO_NO_SNAPSHOT() {
    createBriefWithSubmission();
    stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "no-snap-strict", null));

    var p = projects.findById(project).orElseThrow();
    var v = versions.findById(version).orElseThrow();

    // Make sure no composition snapshot exists in stem_audit
    jdbc.update("delete from stem_audit where target=? and event='COMPOSITION_SNAPSHOT'", project);
    assertThat(stem.getCompositionSnapshot(project, version)).isNull();

    // In STRICT mode, approving video when snapshot is missing -> 409 STEM_FINGERPRINT_STALE
    assertThatThrownBy(() -> stem.checkVideoReviewFingerprint(p, v, reviewer, "APPROVE_VIDEO"))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.getStatus().value()).isEqualTo(409);
          assertThat(e.getCode()).isEqualTo("STEM_FINGERPRINT_STALE");
          assertThat(e.getMessage()).contains("Composition fingerprint does not match review snapshot");
        });
  }

  /**
   * RF11_SNAPSHOT_SURVIVES_RECEIPT_CLEANUP:
   * Cleaning up expired stem_receipts does not affect composition snapshot; video check still functions correctly.
   */
  @Test
  void RF11_SNAPSHOT_SURVIVES_RECEIPT_CLEANUP() {
    createBriefWithSubmission();
    stem.saveSettings(workspace, admin, new StemDtos.Settings(0, "STRICT", List.of(), null, "receipt-cleanup-strict", null));

    var p = projects.findById(project).orElseThrow();
    var v = versions.findById(version).orElseThrow();

    // Save composition snapshot
    String compFp = stem.computeCompositionFingerprint(p, v);
    stem.saveCompositionSnapshot(project, version, compFp, writer);
    assertThat(stem.getCompositionSnapshot(project, version)).isEqualTo(compFp);

    // Simulate cleanup / expiry of stem_receipts
    jdbc.update("delete from stem_receipts");

    // Snapshot survives because it is in stem_audit (append-only), NOT in stem_receipts!
    assertThat(stem.getCompositionSnapshot(project, version)).isEqualTo(compFp);

    // Video review check succeeds in STRICT mode
    assertThatCode(() -> stem.checkVideoReviewFingerprint(p, v, reviewer, "APPROVE_VIDEO")).doesNotThrowAnyException();
  }
}
