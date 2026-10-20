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

    Optional<ScriptVersion> findFirstByProjectIdAndIsCurrentTrue(String projectId);

    Optional<ScriptVersion> findFirstByProjectIdOrderByVersionNoDesc(String projectId);
}
