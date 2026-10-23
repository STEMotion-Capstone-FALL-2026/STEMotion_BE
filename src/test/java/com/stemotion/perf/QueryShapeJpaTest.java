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

    @Autowired com.stemotion.domain.production.repository.PublicationRepository publications;

    @Test
    @DisplayName("paged lists cut in SQL and count with their own query")
    void pagedQueries() {
        ClipProject first = project(physics, "A", ScriptStatus.IN_REVIEW, 1);
        project(physics, "B", ScriptStatus.IN_REVIEW, 1);
        project(physics, "C", ScriptStatus.IN_REVIEW, 1);
        var page = org.springframework.data.domain.PageRequest.of(0, 2);

        var workspacePage = projects.findSummariesByWorkspace(physics.getId(), page);
        assertThat(workspacePage.getContent()).hasSize(2);
        assertThat(workspacePage.getTotalElements()).isEqualTo(3);
        assertThat(projects.findSummariesByWorkspace(physics.getId(), org.springframework.data.domain.PageRequest.of(1, 2))
            .getContent()).hasSize(1);
        assertThat(projects.findSummariesByOwner(writer.getId(), false, MemberStatus.ACTIVE, page).getTotalElements()).isEqualTo(3);
        assertThat(projects.findReviewQueueSummaries(reviewer.getId(), false, ScriptStatus.IN_REVIEW, MemberStatus.ACTIVE,
            User.UserRole.REVIEWER, page).getTotalElements()).isEqualTo(3);
        assertThat(versions.findHistoryWithAuthor(first.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
            .getTotalElements()).isEqualTo(2);
        assertThat(jobs.findHistoryRows(first.getId(), page).getTotalElements()).isZero();
        assertThat(histories.findEventsWithActor(first.getId(), StatusHistory.EventType.REVIEW_DECISION, page)
            .getTotalElements()).isZero();
        assertThat(publications.findLibrary(com.stemotion.domain.production.entity.Publication.Platform.INTERNAL_LIBRARY,
            VideoStatus.PUBLISHED, true, StemSubject.Physics, true, "", page).getTotalElements()).isZero();
    }

    @Autowired com.stemotion.domain.production.repository.PublishRecordRepository publishRecords;

    private com.stemotion.domain.production.entity.Publication published(String title, StemSubject subject, String grade,
                                                                         boolean withClip) {
        ClipProject project = project(physics, title, ScriptStatus.APPROVED, 1);
        project.setSubject(subject);
        project.setGradeLevel(grade);
        project.setVideoStatus(VideoStatus.PUBLISHED);
        com.stemotion.domain.production.entity.Clip clip = null;
        if (withClip) {
            var job = persist(com.stemotion.domain.production.entity.RenderJob.builder().project(project).requestedBy(writer)
                .scriptVersion(versions.findFirstByProjectIdAndIsCurrentTrue(project.getId()).orElseThrow()).build());
            clip = persist(com.stemotion.domain.production.entity.Clip.builder().renderJob(job)
                .videoUrl("https://videos/" + title + ".mp4").build());
        }
        return persist(com.stemotion.domain.production.entity.Publication.builder().project(project).clip(clip)
            .platform(com.stemotion.domain.production.entity.Publication.Platform.INTERNAL_LIBRARY).build());
    }

    @Test
    @DisplayName("library: filters and paging in SQL, the count skips publications without a clip, DISTINCT filters")
    void libraryQueries() {
        published("Ohm", StemSubject.Physics, "Grade 9", true);
        published("Acid", StemSubject.Chemistry, "Grade 10", true);
        published("Cell", StemSubject.Biology, "grade 9", true);
        published("NoClip", StemSubject.Physics, "Grade 9", false);
        var platform = com.stemotion.domain.production.entity.Publication.Platform.INTERNAL_LIBRARY;
        var all = org.springframework.data.domain.PageRequest.of(0, 10);

        var unfiltered = publications.findLibrary(platform, VideoStatus.PUBLISHED, true, StemSubject.Physics, true, "", all);
        assertThat(unfiltered.getTotalElements()).isEqualTo(3);
        assertThat(unfiltered.getContent()).extracting(p -> p.getProject().getTitle()).doesNotContain("NoClip");

        var physicsOnly = publications.findLibrary(platform, VideoStatus.PUBLISHED, false, StemSubject.Physics, true, "", all);
        assertThat(physicsOnly.getContent()).extracting(p -> p.getProject().getTitle()).containsExactly("Ohm");
        assertThat(physicsOnly.getTotalElements()).isEqualTo(1);

        var grade9 = publications.findLibrary(platform, VideoStatus.PUBLISHED, true, StemSubject.Physics, false, "grade 9",
            org.springframework.data.domain.PageRequest.of(0, 1));
        assertThat(grade9.getTotalElements()).isEqualTo(2); // case-insensitive grade
        assertThat(grade9.getContent()).hasSize(1);

        assertThat(publications.findLibrarySubjects(platform, VideoStatus.PUBLISHED))
            .containsExactlyInAnyOrder(StemSubject.Physics, StemSubject.Chemistry, StemSubject.Biology);
        assertThat(publications.findLibraryGrades(platform, VideoStatus.PUBLISHED))
            .containsExactlyInAnyOrder("Grade 9", "Grade 10", "grade 9");
        assertThat(statements(() -> publications.findLibrarySubjects(platform, VideoStatus.PUBLISHED))).isEqualTo(1);
    }

    @Test
    @DisplayName("latest publish record: one row per project, ties on created_at broken by id")
    void latestPublishRecord() {
        ClipProject ohm = project(physics, "Ohm", ScriptStatus.APPROVED, 1);
        ClipProject acid = project(physics, "Acid", ScriptStatus.APPROVED, 1);
        var older = persist(com.stemotion.domain.production.entity.PublishRecord.builder().project(ohm).publishedBy(writer)
            .targets("INTERNAL_LIBRARY").title("v1").build());
        var newer = persist(com.stemotion.domain.production.entity.PublishRecord.builder().project(ohm).publishedBy(writer)
            .targets("INTERNAL_LIBRARY").title("v2").build());
        var a1 = persist(com.stemotion.domain.production.entity.PublishRecord.builder().project(acid).publishedBy(writer)
            .targets("INTERNAL_LIBRARY").title("a1").build());
        var a2 = persist(com.stemotion.domain.production.entity.PublishRecord.builder().project(acid).publishedBy(writer)
            .targets("INTERNAL_LIBRARY").title("a2").build());
        em.flush();
        java.time.LocalDateTime t = java.time.LocalDateTime.of(2026, 10, 20, 9, 0);
        em.createQuery("update PublishRecord r set r.createdAt = :t where r.id = :id")
            .setParameter("t", t).setParameter("id", older.getId()).executeUpdate();
        em.createQuery("update PublishRecord r set r.createdAt = :t where r.id = :id")
            .setParameter("t", t.plusMinutes(5)).setParameter("id", newer.getId()).executeUpdate();
        em.createQuery("update PublishRecord r set r.createdAt = :t where r.project.id = :p")
            .setParameter("t", t).setParameter("p", acid.getId()).executeUpdate();
        em.clear();

        var latest = publishRecords.findLatestByProjectIds(List.of(ohm.getId(), acid.getId()));
        assertThat(latest).hasSize(2);
        assertThat(latest).extracting(r -> r.getTitle()).contains("v2");
        String tieWinner = a1.getId().compareTo(a2.getId()) > 0 ? "a1" : "a2";
        assertThat(latest).extracting(r -> r.getTitle()).contains(tieWinner);
    }

    private static final String NO_CLIP = "00000000-0000-0000-0000-000000000000";

    @Test
    @DisplayName("comments: a page of root notes filtered in SQL, then all their replies in one query")
    void commentPages() {
        ClipProject ohm = project(physics, "Ohm", ScriptStatus.DRAFT, 1);
        var open = com.stemotion.core.enums.StemEnums.FeedbackStatus.OPEN;
        var resolved = com.stemotion.core.enums.StemEnums.FeedbackStatus.RESOLVED;
        List<com.stemotion.domain.review.entity.FeedbackComment> roots = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var root = persist(com.stemotion.domain.review.entity.FeedbackComment.builder().project(ohm).author(reviewer)
                .timestampSec((double) i).content("note " + i).clipId(i == 2 ? "00000000-0000-0000-0000-00000000000b" : null)
                .status(i == 1 ? resolved : open).build());
            roots.add(root);
            persist(com.stemotion.domain.review.entity.FeedbackComment.builder().project(ohm).author(writer).parent(root)
                .timestampSec((double) i).content("reply " + i).status(open).build());
        }
        var firstTwo = org.springframework.data.domain.PageRequest.of(0, 2);
        var page = comments.findRootPage(ohm.getId(), true, open, true, NO_CLIP, firstTwo);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(c -> c.getContent()).containsExactly("note 0", "note 1");

        var five = org.springframework.data.domain.PageRequest.of(0, 5);
        assertThat(comments.findRootPage(ohm.getId(), false, open, true, NO_CLIP, five).getTotalElements()).isEqualTo(2);
        assertThat(comments.findRootPage(ohm.getId(), true, open, false, "00000000-0000-0000-0000-00000000000a", five).getContent())
            .extracting(c -> c.getContent()).containsExactly("note 0", "note 1");

        var ids = List.of(roots.get(0).getId(), roots.get(1).getId());
        assertThat(comments.findRepliesOf(ids)).extracting(c -> c.getContent()).containsExactlyInAnyOrder("reply 0", "reply 1");
        assertThat(statements(() -> comments.findRepliesOf(ids).forEach(c -> c.getAuthor().getFullName()))).isEqualTo(1);
    }
}
