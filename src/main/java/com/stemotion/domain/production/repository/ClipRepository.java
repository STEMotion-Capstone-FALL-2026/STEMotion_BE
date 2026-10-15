package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.Clip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ClipRepository extends JpaRepository<Clip, String> {
    Optional<Clip> findByRenderJobId(String renderJobId);

    @Query("select c from Clip c join fetch c.renderJob r where r.project.id = :projectId "
        + "and r.scriptVersion.id = :versionId and c.videoUrl = :videoUrl order by c.createdAt desc")
    List<Clip> findCurrentArtifacts(@Param("projectId") String projectId,
        @Param("versionId") String versionId, @Param("videoUrl") String videoUrl);
}
