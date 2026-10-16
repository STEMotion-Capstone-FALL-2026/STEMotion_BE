package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.Composition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompositionRepository extends JpaRepository<Composition, String> {

    Optional<Composition> findByScriptVersionId(String scriptVersionId);

    /** Row lock for a save, so two producers cannot both write the next revision. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Composition c WHERE c.scriptVersion.id = :versionId")
    Optional<Composition> lockByScriptVersionId(@Param("versionId") String versionId);
}
