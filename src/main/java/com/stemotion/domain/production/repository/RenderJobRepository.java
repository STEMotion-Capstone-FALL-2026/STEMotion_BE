package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RenderJobRepository extends JpaRepository<RenderJob, String> {

    @Query("select j.project.id from RenderJob j where j.id = :id")
    Optional<String> findProjectIdById(@Param("id") String id);

    List<RenderJob> findByProjectIdOrderByCreatedAtDesc(String projectId);

    /** Render history rows with the script version number, without input props, in one query. */
    @Query("select new com.stemotion.domain.production.repository.RenderJobRow(j.id, j.project.id, j.status, "
        + "j.progress, j.stage, j.videoUrl, j.errorMessage, j.createdAt, v.versionNo, j.compositionRevision) "
        + "from RenderJob j left join j.scriptVersion v where j.project.id = :projectId order by j.createdAt desc")
    List<RenderJobRow> findHistoryRows(@Param("projectId") String projectId);

    /** The most recent jobs with project and requester, for the admin queue (DB performance plan 2.10). */
    @Query("select j from RenderJob j join fetch j.project left join fetch j.requestedBy order by j.createdAt desc")
    List<RenderJob> findRecentWithProject(org.springframework.data.domain.Pageable page);

    @Query("select j.status, count(j) from RenderJob j group by j.status")
    List<Object[]> countByStatus();

    /** Jobs that ended (or changed) since a moment, counted per status. */
    @Query("select j.status, count(j) from RenderJob j where j.updatedAt >= :since group by j.status")
    List<Object[]> countByStatusUpdatedSince(@Param("since") java.time.LocalDateTime since);

    Optional<RenderJob> findByExternalJobId(String externalJobId);

    boolean existsByProjectIdAndStatusIn(String projectId, java.util.Collection<RenderStatus> statuses);

    /**
     * Column updates for a job the worker may already be reporting on: a merge of a loaded copy
     * could overwrite progress a callback wrote in between.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RenderJob j SET j.externalJobId = :externalJobId WHERE j.id = :id")
    int setExternalJobId(@Param("id") String id, @Param("externalJobId") String externalJobId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RenderJob j SET j.status = :failed, j.errorMessage = :message WHERE j.id = :id AND j.status = :queued")
    int failQueued(@Param("id") String id, @Param("message") String message,
                   @Param("queued") RenderStatus queued, @Param("failed") RenderStatus failed);
}
