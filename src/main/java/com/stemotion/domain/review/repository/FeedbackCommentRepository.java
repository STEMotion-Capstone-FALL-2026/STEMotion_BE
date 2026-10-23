package com.stemotion.domain.review.repository;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.domain.review.entity.FeedbackComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackCommentRepository extends JpaRepository<FeedbackComment, String> {

    @org.springframework.data.jpa.repository.Query("select c.project.id from FeedbackComment c where c.id = :id")
    java.util.Optional<String> findProjectIdById(@org.springframework.data.repository.query.Param("id") String id);

    List<FeedbackComment> findByProjectIdOrderByTimestampSecAsc(String projectId);

    List<FeedbackComment> findByProjectIdAndStatusOrderByTimestampSecAsc(String projectId, FeedbackStatus status);

    List<FeedbackComment> findBySceneIdOrderByCreatedAtAsc(String sceneId);

    /** A project's notes and replies with author, resolver, scene and parent, in one query. */
    @org.springframework.data.jpa.repository.Query("select c from FeedbackComment c join fetch c.author left join fetch c.resolvedBy "
        + "left join fetch c.scene left join fetch c.parent where c.project.id = :projectId order by c.timestampSec asc")
    List<FeedbackComment> findForProjectWithPeople(@org.springframework.data.repository.query.Param("projectId") String projectId);

    /**
     * One page of a project's notes (replies excluded), filtered in SQL. Flags stand in for
     * optional filters so no null enum parameter reaches Postgres. A clip filter keeps that clip's
     * notes and the unscoped ones (script and web-player notes), as the unpaged list does.
     */
    String ROOTS_WHERE = "where c.project.id = :projectId and c.parent is null "
        + "and (:anyStatus = true or c.status = :status) "
        + "and (:anyClip = true or c.clipId is null or c.clipId = :clipId) ";

    @org.springframework.data.jpa.repository.Query(value = "select c from FeedbackComment c join fetch c.author left join fetch c.resolvedBy left join fetch c.scene "
        + ROOTS_WHERE + "order by c.timestampSec asc nulls last, c.createdAt asc, c.id asc",
        countQuery = "select count(c) from FeedbackComment c " + ROOTS_WHERE)
    org.springframework.data.domain.Page<FeedbackComment> findRootPage(@org.springframework.data.repository.query.Param("projectId") String projectId,
        @org.springframework.data.repository.query.Param("anyStatus") boolean anyStatus, @org.springframework.data.repository.query.Param("status") FeedbackStatus status,
        @org.springframework.data.repository.query.Param("anyClip") boolean anyClip, @org.springframework.data.repository.query.Param("clipId") String clipId, org.springframework.data.domain.Pageable page);

    /** Replies of several notes with their people, in one query. */
    @org.springframework.data.jpa.repository.Query("select c from FeedbackComment c join fetch c.author left join fetch c.resolvedBy left join fetch c.scene "
        + "join fetch c.parent p where p.id in :parentIds order by c.createdAt asc, c.id asc")
    List<FeedbackComment> findRepliesOf(@org.springframework.data.repository.query.Param("parentIds") java.util.Collection<String> parentIds);

    /** A scene's notes with the same associations, in one query. */
    @org.springframework.data.jpa.repository.Query("select c from FeedbackComment c join fetch c.author left join fetch c.resolvedBy "
        + "left join fetch c.scene left join fetch c.parent where c.scene.id = :sceneId order by c.createdAt asc")
    List<FeedbackComment> findForSceneWithPeople(@org.springframework.data.repository.query.Param("sceneId") String sceneId);

    long countByProjectIdAndStatus(String projectId, FeedbackStatus status);

    /** Open findings not tied to a rendered Clip: script notes and web-player notes. */
    long countByProjectIdAndStatusAndParentIsNullAndClipIdIsNull(String projectId, FeedbackStatus status);

    long countByProjectIdAndStatusAndParentIsNullAndClipId(String projectId, FeedbackStatus status, String clipId);
}
