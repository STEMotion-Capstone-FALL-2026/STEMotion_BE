package com.stemotion.perf;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.UpdateMemberRequest;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import com.stemotion.domain.workspace.service.WorkspaceService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Master update plan T01/T02/T12: the row locks behind membership changes and draft revisions,
 * proven with two real transactions racing on a real Postgres (embedded, throwaway; never the
 * shared database). The schema is built by the Flyway migrations V1..latest and Hibernate
 * validates the entities against it, so this also checks the migrations on Postgres itself.
 */
@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // each racer commits its own transaction
class PostgresConcurrencyTest {

    private static EmbeddedPostgres postgres;

    private static synchronized EmbeddedPostgres postgres() throws Exception {
        if (postgres == null) postgres = EmbeddedPostgres.builder().start();
        return postgres;
    }

    @AfterAll
    static void stop() throws Exception {
        if (postgres != null) postgres.close();
        postgres = null;
    }

    @TestConfiguration
    static class Pg {
        @Bean
        DataSource dataSource() throws Exception {
            // Same driver setting as application.yml (enums and jsonb are sent untyped).
            return postgres().getDatabase("postgres", "postgres", Map.of("stringtype", "unspecified"));
        }
    }

    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;
    @Autowired ClipProjectRepository projects;
    @Autowired ScriptVersionRepository versions;
    @Autowired SceneRepository scenes;
    @Autowired com.stemotion.domain.production.repository.CompositionRepository compositions;

    WorkspaceService workspaceService;
    ScriptService scriptService;
    TransactionTemplate tx;

    @BeforeEach
    void wire() {
        tx = new TransactionTemplate(transactions);
        workspaceService = new WorkspaceService(workspaces, members, projects, users);
        scriptService = new ScriptService(projects, versions, scenes,
            new SceneMapper(new com.fasterxml.jackson.databind.ObjectMapper(), new ScenePropsValidator()),
            mock(AuthService.class), workspaceService, mock(StatusHistoryService.class), mock(WorkspaceAccess.class));
    }

    private User user(String name, User.UserRole role) {
        return users.save(User.builder().fullName(name).email(name + "-" + UUID.randomUUID() + "@test.invalid")
            .role(role).status(User.UserStatus.ACTIVE).build());
    }

    private WorkspaceMember seat(Workspace workspace, User user, User.UserRole role, boolean leader) {
        return members.save(WorkspaceMember.builder().workspace(workspace).user(user).role(role)
            .isLeader(leader).status(MemberStatus.ACTIVE).build());
    }

    /** Runs both calls at the same moment, each in its own transaction; returns what each threw (or null). */
    private List<Throwable> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> results = new ArrayList<>();
            for (Callable<?> call : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        tx.execute(status -> {
                            try { return call.call(); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
                        });
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> result : results) outcomes.add(result.get(30, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Flyway V1..latest builds the schema on Postgres and the entities validate against it")
    void migrationsBuildAValidSchema() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        Integer failed = jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(22);
        assertThat(failed).isZero();
        assertThat(jdbc.queryForObject("select column_default from information_schema.columns "
            + "where table_name = 'script_versions' and column_name = 'draft_revision'", String.class)).isEqualTo("0");
    }

    @Test
    @DisplayName("two admins removing the only two reviewers at once: one succeeds, a reviewer always remains")
    void concurrentRemovalsKeepTheLastReviewer() throws Exception {
        Workspace workspace = workspaces.save(Workspace.builder().name("Race " + UUID.randomUUID())
            .createdBy(user("admin", User.UserRole.ADMIN)).build());
        seat(workspace, user("writer", User.UserRole.WRITER), User.UserRole.WRITER, true);
        seat(workspace, user("producer", User.UserRole.PRODUCER), User.UserRole.PRODUCER, false);
        WorkspaceMember first = seat(workspace, user("reviewer1", User.UserRole.REVIEWER), User.UserRole.REVIEWER, false);
        WorkspaceMember second = seat(workspace, user("reviewer2", User.UserRole.REVIEWER), User.UserRole.REVIEWER, false);

        List<Throwable> outcomes = race(
            () -> { workspaceService.removeMember(workspace.getId(), first.getId()); return null; },
            () -> { workspaceService.removeMember(workspace.getId(), second.getId()); return null; });

        assertThat(outcomes).filteredOn(t -> t == null).hasSize(1);
        assertThat(outcomes).filteredOn(t -> t != null).singleElement()
            .isInstanceOf(ApiException.class).extracting(Throwable::getMessage).asString().contains("[REVIEWER]");
        assertThat(jdbc.queryForObject("select count(*) from workspace_members where workspace_id = ? and role = 'REVIEWER' "
            + "and status = 'ACTIVE'", Integer.class, workspace.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("deactivating one reviewer while re-roling the other at once never leaves zero reviewers")
    void concurrentDeactivateAndRoleChangeKeepCoverage() throws Exception {
        User admin = user("admin", User.UserRole.ADMIN);
        Workspace workspace = workspaces.save(Workspace.builder().name("Race " + UUID.randomUUID()).createdBy(admin).build());
        seat(workspace, user("writer", User.UserRole.WRITER), User.UserRole.WRITER, true);
        seat(workspace, user("producer", User.UserRole.PRODUCER), User.UserRole.PRODUCER, false);
        WorkspaceMember first = seat(workspace, user("reviewer1", User.UserRole.REVIEWER), User.UserRole.REVIEWER, false);
        WorkspaceMember second = seat(workspace, user("reviewer2", User.UserRole.REVIEWER), User.UserRole.REVIEWER, false);

        List<Throwable> outcomes = race(
            () -> workspaceService.updateMember(workspace.getId(), first.getId(),
                new UpdateMemberRequest(null, MemberStatus.INACTIVE, null), admin.getId()),
            () -> workspaceService.updateMember(workspace.getId(), second.getId(),
                new UpdateMemberRequest(User.UserRole.PRODUCER, null, null), admin.getId()));

        assertThat(outcomes).filteredOn(t -> t == null).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from workspace_members where workspace_id = ? and role = 'REVIEWER' "
            + "and status = 'ACTIVE'", Integer.class, workspace.getId())).isEqualTo(1);
    }

    private ClipProject draftProject(int draftRevision) {
        User writer = user("writer", User.UserRole.WRITER);
        Workspace workspace = workspaces.save(Workspace.builder().name("Draft " + UUID.randomUUID()).createdBy(writer).build());
        ClipProject project = projects.save(ClipProject.builder().title("Original").subject(StemSubject.Math).fps(30)
            .scriptStatus(ScriptStatus.DRAFT).videoStatus(VideoStatus.NOT_RENDERED).archived(false)
            .workspace(workspace).owner(writer).build());
        versions.save(ScriptVersion.builder().versionNo(1).title("Original").project(project).createdBy(writer)
            .draftRevision(draftRevision).build());
        return project;
    }

    private int draftRevision(ClipProject project) {
        return jdbc.queryForObject("select draft_revision from script_versions where project_id = ? and is_current",
            Integer.class, project.getId());
    }

    @Test
    @DisplayName("two saves from the same draft revision at once: one is applied, the other is STALE_REVISION")
    void concurrentDraftSavesFromOneRevision() throws Exception {
        ClipProject project = draftProject(3);
        List<Throwable> outcomes = race(
            () -> scriptService.updateDraft(project.getId(), new UpdateScriptRequest("Tab A", null, null, null, null, null, 3)),
            () -> scriptService.updateDraft(project.getId(), new UpdateScriptRequest("Tab B", null, null, null, null, null, 3)));

        assertThat(outcomes).filteredOn(t -> t == null).hasSize(1);
        assertThat(outcomes).filteredOn(t -> t != null).singleElement()
            .isInstanceOf(ApiException.class).extracting(t -> ((ApiException) t).getCode()).isEqualTo("STALE_REVISION");
        assertThat(draftRevision(project)).as("bumped exactly once").isEqualTo(4);
        String title = jdbc.queryForObject("select title from script_versions where project_id = ?", String.class, project.getId());
        assertThat(title).isIn("Tab A", "Tab B");
    }

    @Test
    @DisplayName("durations measured from a draft that was saved meanwhile are refused; a rolled-back save does not bump")
    void staleMeasurementAndRollback() {
        ClipProject project = draftProject(5);
        String measuredVersionId = versions.findFirstByProjectIdAndIsCurrentTrue(project.getId()).orElseThrow().getId();
        // The writer saves while TTS/Whisper is measuring revision 5.
        tx.executeWithoutResult(s -> scriptService.updateDraft(project.getId(),
            new UpdateScriptRequest("Edited during sync", null, null, null, null, null, 5)));
        assertThat(draftRevision(project)).isEqualTo(6);

        Throwable stale = catchThrowable(() -> tx.executeWithoutResult(s ->
            scriptService.applyMeasuredDurations(project.getId(), measuredVersionId, Map.of(), 5)));
        assertThat(stale).isInstanceOf(ApiException.class);
        assertThat(((ApiException) stale).getDetails()).containsEntry("currentRevision", 6).containsEntry("baseRevision", 5);
        assertThat(draftRevision(project)).as("refused sync changes nothing").isEqualTo(6);

        // A save whose transaction rolls back leaves the revision where it was.
        tx.executeWithoutResult(s -> {
            scriptService.updateDraft(project.getId(), new UpdateScriptRequest("Rolled back", null, null, null, null, null, 6));
            s.setRollbackOnly();
        });
        assertThat(draftRevision(project)).isEqualTo(6);
    }

    private ScriptVersion replaceVersion(ClipProject project, ScriptStatus status) {
        return tx.execute(s -> {
            ScriptVersion old = versions.findFirstByProjectIdAndIsCurrentTrue(project.getId()).orElseThrow();
            old.setIsCurrent(false);
            versions.saveAndFlush(old);
            ScriptVersion next = versions.saveAndFlush(ScriptVersion.builder().project(old.getProject())
                .createdBy(old.getCreatedBy()).versionNo(old.getVersionNo() + 1).title("Replacement")
                .draftRevision(0).status(status).isCurrent(true).build());
            ClipProject updated = projects.findById(project.getId()).orElseThrow();
            updated.setScriptStatus(status);
            projects.save(updated);
            return next;
        });
    }

    @Test
    void measurementOfVersionOneCannotWriteVersionTwoAtTheSameRevision() {
        ClipProject project = draftProject(0);
        String measuredId = versions.findFirstByProjectIdAndIsCurrentTrue(project.getId()).orElseThrow().getId();
        // The review/return commits in a different transaction while the worker measures v1/r0.
        ScriptVersion next = replaceVersion(project, ScriptStatus.CHANGE_REQUESTED);
        var copied = scenes.saveAndFlush(com.stemotion.domain.script.entity.Scene.builder()
            .scriptVersion(next).orderNo(1).title("Copied scene").narration("Changed narration")
            .sceneType(SceneType.STEM_QUIZ).props("{}").durationInFrames(120).build());

        Throwable stale = catchThrowable(() -> tx.executeWithoutResult(s -> scriptService.applyMeasuredDurations(
            project.getId(), measuredId, Map.of(copied.getId(), 999), 0)));
        assertThat(stale).isInstanceOf(ApiException.class);
        assertThat(((ApiException)stale).getDetails()).containsEntry("baseVersionId", measuredId)
            .containsEntry("currentVersionId", next.getId()).containsEntry("baseRevision", 0)
            .containsEntry("currentRevision", 0);
        assertThat(draftRevision(project)).isZero();
        assertThat(scenes.findById(copied.getId()).orElseThrow().getDurationInFrames()).isEqualTo(120);
        // A measurement of the new identity still succeeds.
        tx.executeWithoutResult(s -> scriptService.applyMeasuredDurations(project.getId(), next.getId(),
            Map.of(copied.getId(), 150), 0));
        assertThat(draftRevision(project)).isEqualTo(1);
        assertThat(scenes.findById(copied.getId()).orElseThrow().getDurationInFrames()).isEqualTo(150);
    }

    @Test
    void measurementOfOldApprovedVersionCannotWriteReplacementComposition() throws Exception {
        ClipProject project = draftProject(0);
        String measuredId = versions.findFirstByProjectIdAndIsCurrentTrue(project.getId()).orElseThrow().getId();
        ScriptVersion next = replaceVersion(project, ScriptStatus.APPROVED);
        String original = "[{\"id\":\"scene\",\"durationInFrames\":120,\"narration\":\"New narration\"}]";
        var saved = compositions.saveAndFlush(com.stemotion.domain.production.entity.Composition.builder()
            .project(project).scriptVersion(next).revision(1).scenes(original).build());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var service = new com.stemotion.domain.production.service.CompositionService(compositions, scriptService,
            new SceneMapper(mapper, new ScenePropsValidator()), mock(AuthService.class), mapper);
        Throwable stale = catchThrowable(() -> tx.executeWithoutResult(s -> service.applyMeasured(project.getId(),
            measuredId, 1, Map.of("scene", 999), Map.of("scene", List.of()), project.getOwner().getId())));
        assertThat(stale).isInstanceOf(ApiException.class);
        assertThat(((ApiException)stale).getDetails()).containsEntry("resource", "COMPOSITION")
            .containsEntry("baseVersionId", measuredId).containsEntry("currentVersionId", next.getId())
            .containsEntry("currentRevision", 1).containsEntry("baseRevision", 1);
        var unchanged = compositions.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getRevision()).isEqualTo(1);
        assertThat(mapper.readTree(unchanged.getScenes())).isEqualTo(mapper.readTree(original));
    }

    private static Throwable catchThrowable(Runnable run) {
        try { run.run(); return null; } catch (Throwable t) { return t; }
    }
}
