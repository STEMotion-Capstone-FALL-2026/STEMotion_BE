package com.stemotion.domain.script.repository;

import com.stemotion.domain.script.entity.ScriptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptVersionRepository extends JpaRepository<ScriptVersion, String> {

    List<ScriptVersion> findByProjectIdOrderByVersionNoDesc(String projectId);

    /** Version history with each version's author, in one query. */
    @org.springframework.data.jpa.repository.Query("select v from ScriptVersion v left join fetch v.createdBy "
        + "where v.project.id = :projectId order by v.versionNo desc")
    List<ScriptVersion> findHistoryWithAuthor(@org.springframework.data.repository.query.Param("projectId") String projectId);

    @org.springframework.data.jpa.repository.Query(value = "select v from ScriptVersion v left join fetch v.createdBy where v.project.id = :projectId order by v.versionNo desc",
        countQuery = "select count(v) from ScriptVersion v where v.project.id = :projectId")
    org.springframework.data.domain.Page<ScriptVersion> findHistoryWithAuthor(@org.springframework.data.repository.query.Param("projectId") String projectId, org.springframework.data.domain.Pageable page);

    Optional<ScriptVersion> findFirstByProjectIdAndIsCurrentTrue(String projectId);

    /** The current version with a row lock, so two draft saves cannot both pass the revision check. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from ScriptVersion v where v.project.id = :projectId and v.isCurrent = true order by v.versionNo desc")
    List<ScriptVersion> lockCurrentRows(@org.springframework.data.repository.query.Param("projectId") String projectId);

    /** Newest current version, locked; like findFirstByProjectIdAndIsCurrentTrue, never fails on two flagged rows. */
    default Optional<ScriptVersion> lockCurrentByProjectId(String projectId) {
        return lockCurrentRows(projectId).stream().findFirst();
    }

    Optional<ScriptVersion> findFirstByProjectIdOrderByVersionNoDesc(String projectId);
}
