package com.stemotion.domain.project.repository;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.domain.project.entity.ClipProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClipProjectRepository extends JpaRepository<ClipProject, String> {

    /**
     * Project lists (DB performance plan 2.2): owner name and scene count come with the row instead
     * of two queries per project. The scene count is of the current version, or of the latest one
     * when none is marked current, as ScriptService.currentVersion reads it.
     */
    String SCENE_COUNT = "(select count(s) from Scene s where s.scriptVersion.project = p and s.scriptVersion.versionNo = "
        + "  (select max(v.versionNo) from ScriptVersion v where v.project = p and (v.isCurrent = true or not exists "
        + "    (select c from ScriptVersion c where c.project = p and c.isCurrent = true))))";

    String SUMMARY_SELECT = "select new com.stemotion.domain.project.repository.ProjectSummaryRow("
        + "p.id, p.title, p.subject, p.gradeLevel, p.totalDurationSeconds, p.scriptStatus, p.videoStatus, o.fullName, "
        + SCENE_COUNT + ", p.updatedAt) from ClipProject p join p.owner o ";

    /** Scene counts of several projects in one query: rows of (projectId, count). */
    @org.springframework.data.jpa.repository.Query("select p.id, " + SCENE_COUNT + " from ClipProject p where p.id in :ids")
    List<Object[]> sceneCounts(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

    @org.springframework.data.jpa.repository.Query(SUMMARY_SELECT
        + "where p.workspace.id = :workspaceId and (p.archived is null or p.archived = false) order by p.createdAt desc")
    List<ProjectSummaryRow> findSummariesByWorkspace(
        @org.springframework.data.repository.query.Param("workspaceId") String workspaceId);

    /** The owner's projects in workspaces they can still read (an admin reads all). */
    @org.springframework.data.jpa.repository.Query(SUMMARY_SELECT
        + "where o.id = :ownerId and (p.archived is null or p.archived = false) and (:admin = true or exists "
        + "(select m from WorkspaceMember m where m.workspace = p.workspace and m.user.id = :ownerId and m.status = :active)) "
        + "order by p.createdAt desc")
    List<ProjectSummaryRow> findSummariesByOwner(
        @org.springframework.data.repository.query.Param("ownerId") String ownerId,
        @org.springframework.data.repository.query.Param("admin") boolean admin,
        @org.springframework.data.repository.query.Param("active") com.stemotion.core.enums.StemEnums.MemberStatus active);

    /** Scripts in review in the workspaces where the user is an active reviewer (an admin sees all). */
    @org.springframework.data.jpa.repository.Query(SUMMARY_SELECT
        + "where p.scriptStatus = :inReview and (p.archived is null or p.archived = false) and (:admin = true or exists "
        + "(select m from WorkspaceMember m where m.workspace = p.workspace and m.user.id = :userId "
        + "and m.status = :active and m.role = :reviewer)) order by p.createdAt desc")
    List<ProjectSummaryRow> findReviewQueueSummaries(
        @org.springframework.data.repository.query.Param("userId") String userId,
        @org.springframework.data.repository.query.Param("admin") boolean admin,
        @org.springframework.data.repository.query.Param("inReview") ScriptStatus inReview,
        @org.springframework.data.repository.query.Param("active") com.stemotion.core.enums.StemEnums.MemberStatus active,
        @org.springframework.data.repository.query.Param("reviewer") com.stemotion.domain.auth.entity.User.UserRole reviewer);

    /** The ids access checks need, without loading the project row. */
    @org.springframework.data.jpa.repository.Query("select new com.stemotion.core.security.ProjectRef(p.id, p.workspace.id, p.owner.id) "
        + "from ClipProject p where p.id = :id")
    java.util.Optional<com.stemotion.core.security.ProjectRef> findRefById(@org.springframework.data.repository.query.Param("id") String id);

    List<ClipProject> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    List<ClipProject> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<ClipProject> findByScriptStatusOrderByCreatedAtDesc(ScriptStatus scriptStatus);

    List<ClipProject> findByVideoStatusOrderByCreatedAtDesc(VideoStatus videoStatus);

    long countByWorkspaceId(String workspaceId);

    /** Projects per subject: rows of (subject, count). */
    @org.springframework.data.jpa.repository.Query("select p.subject, count(p) from ClipProject p group by p.subject")
    List<Object[]> countBySubject();

    /** Project count per workspace in one grouped query: rows of (workspaceId, count). */
    @org.springframework.data.jpa.repository.Query("select p.workspace.id, count(p) from ClipProject p where p.workspace.id in :ids group by p.workspace.id")
    List<Object[]> countByWorkspaceIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

}
