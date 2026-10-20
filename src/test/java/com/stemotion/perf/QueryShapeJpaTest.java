package com.stemotion.perf;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.metrics.QueryCountInspector;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.entity.StatusHistory;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.repository.ProjectSummaryRow;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB performance plan, step 2: the new JPQL queries start (every @Query of every repository is
 * parsed when the context loads), return what the old per-row code returned, and keep a constant
 * number of statements as rows grow. Runs on H2 in PostgreSQL mode; no real database is touched.
 */
@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.stemotion.core.metrics.QueryCountInspector"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QueryShapeJpaTest {

    /** Own H2 data source, so the main config's Postgres driver and stringtype setting do not apply. */
    @org.springframework.boot.test.context.TestConfiguration
    static class H2 {
        @org.springframework.context.annotation.Bean
        javax.sql.DataSource dataSource() {
            var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:queryshape;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;"
                    + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS VARCHAR", "sa", "");
            ds.setDriverClassName("org.h2.Driver");
            return ds;
        }
    }

    @Autowired EntityManager em;
    @Autowired ClipProjectRepository projects;
    @Autowired WorkspaceMemberRepository members;
    @Autowired ScriptVersionRepository versions;
    @Autowired StatusHistoryRepository histories;
    @Autowired FeedbackCommentRepository comments;
    @Autowired RenderJobRepository jobs;

    User writer;
    User reviewer;
    Workspace physics;
    Workspace chemistry;

    @BeforeEach
    void seed() {
        writer = persist(User.builder().fullName("Writer").email("writer@test.invalid").build());
        reviewer = persist(User.builder().fullName("Reviewer").email("reviewer@test.invalid").build());
        physics = persist(Workspace.builder().name("Physics").department("Physics").createdBy(writer).build());
        chemistry = persist(Workspace.builder().name("Chemistry").department("Chemistry").createdBy(writer).build());
        persist(WorkspaceMember.builder().workspace(physics).user(writer).role(User.UserRole.WRITER)
            .status(MemberStatus.ACTIVE).isLeader(true).build());
        persist(WorkspaceMember.builder().workspace(physics).user(reviewer).role(User.UserRole.REVIEWER)
            .status(MemberStatus.ACTIVE).build());
        // The writer left chemistry: their old project there must disappear from "mine".
        persist(WorkspaceMember.builder().workspace(chemistry).user(writer).role(User.UserRole.WRITER)
            .status(MemberStatus.INACTIVE).build());
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    /** A project with an older version (2 scenes) and a current one with the given scene count. */
    private ClipProject project(Workspace workspace, String title, ScriptStatus status, int currentScenes) {
        ClipProject project = persist(ClipProject.builder().title(title).subject(StemSubject.Physics).fps(30)
            .totalDurationSeconds(60).scriptStatus(status).videoStatus(VideoStatus.NOT_RENDERED).archived(false)
            .owner(writer).workspace(workspace).build());
        ScriptVersion old = persist(ScriptVersion.builder().versionNo(1).title(title).status(ScriptStatus.CHANGE_REQUESTED)
            .isCurrent(false).project(project).createdBy(writer).build());
        scenes(old, 2);
        ScriptVersion current = persist(ScriptVersion.builder().versionNo(2).title(title).status(status)
            .isCurrent(true).project(project).createdBy(writer).build());
        scenes(current, currentScenes);
        return project;
    }

    private void scenes(ScriptVersion version, int count) {
        for (int i = 1; i <= count; i++) {
            persist(Scene.builder().orderNo(i).orderIndex(i).sceneType(SceneType.TITLE_HERO).title("Scene " + i)
                .durationInFrames(90).scriptVersion(version).build());
        }
    }

    private int statements(Runnable work) {
        em.flush();
        em.clear();
        QueryCountInspector.reset();
        work.run();
        return QueryCountInspector.count();
    }

    @Test
    @DisplayName("project lists: owner name and current scene count in one statement, scoped in SQL")
    void projectSummaries() {
        ClipProject inReview = project(physics, "Ohm", ScriptStatus.IN_REVIEW, 3);
        project(chemistry, "Acids", ScriptStatus.IN_REVIEW, 1);
        ClipProject archived = project(physics, "Old", ScriptStatus.DRAFT, 1);
        archived.setArchived(true);

        List<ProjectSummaryRow> physicsRows = projects.findSummariesByWorkspace(physics.getId());
        assertThat(physicsRows).extracting(ProjectSummaryRow::title).containsExactly("Ohm");
        assertThat(physicsRows.get(0).sceneCount()).isEqualTo(3L);
        assertThat(physicsRows.get(0).ownerName()).isEqualTo("Writer");

        assertThat(projects.findSummariesByOwner(writer.getId(), false, MemberStatus.ACTIVE))
            .extracting(ProjectSummaryRow::title).containsExactly("Ohm");
        assertThat(projects.findSummariesByOwner(writer.getId(), true, MemberStatus.ACTIVE))
            .extracting(ProjectSummaryRow::title).containsExactlyInAnyOrder("Ohm", "Acids");

        assertThat(projects.findReviewQueueSummaries(reviewer.getId(), false, ScriptStatus.IN_REVIEW,
            MemberStatus.ACTIVE, User.UserRole.REVIEWER)).extracting(ProjectSummaryRow::id).containsExactly(inReview.getId());
        assertThat(projects.findReviewQueueSummaries(writer.getId(), false, ScriptStatus.IN_REVIEW,
            MemberStatus.ACTIVE, User.UserRole.REVIEWER)).isEmpty();

        assertThat(statements(() -> projects.findSummariesByWorkspace(physics.getId()))).isEqualTo(1);
        for (int i = 0; i < 15; i++) project(physics, "Extra " + i, ScriptStatus.DRAFT, 2);
        assertThat(statements(() -> projects.findSummariesByWorkspace(physics.getId()))).isEqualTo(1);
        assertThat(projects.sceneCounts(List.of(inReview.getId()))).hasSize(1);
    }

    @Test
    @DisplayName("access checks and workspace lists read memberships, refs and counts in constant statements")
    void accessAndWorkspaceCounts() {
        ClipProject ohm = project(physics, "Ohm", ScriptStatus.DRAFT, 1);
        project(physics, "Waves", ScriptStatus.DRAFT, 1);

        assertThat(members.findMembershipsByUserId(writer.getId()))
            .extracting(m -> m.workspaceId() + ":" + m.role() + ":" + m.status() + ":" + m.leader())
            .containsExactlyInAnyOrder(physics.getId() + ":WRITER:ACTIVE:true", chemistry.getId() + ":WRITER:INACTIVE:false");
        assertThat(projects.findRefById(ohm.getId())).get()
            .satisfies(ref -> assertThat(ref.workspaceId()).isEqualTo(physics.getId()))
            .satisfies(ref -> assertThat(ref.ownerId()).isEqualTo(writer.getId()));

        Map<String, Long> memberCounts = members.countByWorkspaceIds(List.of(physics.getId(), chemistry.getId())).stream()
            .collect(Collectors.toMap(r -> String.valueOf(r[0]), r -> ((Number) r[1]).longValue()));
        assertThat(memberCounts).containsEntry(physics.getId(), 2L).containsEntry(chemistry.getId(), 1L);
        Map<String, Long> projectCounts = projects.countByWorkspaceIds(List.of(physics.getId(), chemistry.getId())).stream()
            .collect(Collectors.toMap(r -> String.valueOf(r[0]), r -> ((Number) r[1]).longValue()));
        assertThat(projectCounts).containsEntry(physics.getId(), 2L).doesNotContainKey(chemistry.getId());

        assertThat(statements(() -> members.findWithWorkspaceByUserId(writer.getId())
            .forEach(m -> m.getWorkspace().getName()))).isEqualTo(1);
        assertThat(statements(() -> members.findWithUserByWorkspaceId(physics.getId())
            .forEach(m -> m.getUser().getFullName()))).isEqualTo(1);
        assertThat(statements(() -> versions.findHistoryWithAuthor(ohm.getId())
            .forEach(v -> v.getCreatedBy().getFullName()))).isEqualTo(1);
    }

    @Test
    @DisplayName("history, comments and render rows load their associations with the rows")
    void historyCommentsAndJobs() {
        ClipProject ohm = project(physics, "Ohm", ScriptStatus.DRAFT, 1);
        for (int i = 0; i < 3; i++) {
            persist(StatusHistory.builder().project(ohm).changedBy(reviewer)
                .eventType(StatusHistory.EventType.REVIEW_DECISION).targetType(StatusHistory.TargetType.SCRIPT)
                .targetId("v").decision(StatusHistory.Decision.REJECTED).reason("r" + i)
                .toStatus(com.stemotion.domain.project.entity.ProjectStatus.DRAFT).build());
        }
        assertThat(statements(() -> histories.findEventsWithActor(ohm.getId(), StatusHistory.EventType.REVIEW_DECISION)
            .forEach(h -> h.getChangedBy().getFullName()))).isEqualTo(1);
        assertThat(statements(() -> comments.findForProjectWithPeople(ohm.getId()))).isEqualTo(1);
        assertThat(statements(() -> jobs.findHistoryRows(ohm.getId()))).isEqualTo(1);
        assertThat(statements(() -> jobs.countByStatus())).isEqualTo(1);
        assertThat(statements(() -> projects.countBySubject())).isEqualTo(1);
    }
}
