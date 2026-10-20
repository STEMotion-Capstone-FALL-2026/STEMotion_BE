package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.Clip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ClipRepository extends JpaRepository<Clip, String> {
    Optional<Clip> findByRenderJobId(String renderJobId);

    /** Clips with the render job and script version they came from, in one query. */
    @Query("select c from Clip c join fetch c.renderJob r left join fetch r.scriptVersion where c.id in :ids")
    List<Clip> findWithRenderJobByIdIn(@Param("ids") java.util.Collection<String> ids);

    @Query("select c from Clip c join fetch c.renderJob r where r.project.id = :projectId "
        + "and r.scriptVersion.id = :versionId and c.videoUrl = :videoUrl order by c.createdAt desc")
    List<Clip> findCurrentArtifacts(@Param("projectId") String projectId,
        @Param("versionId") String versionId, @Param("videoUrl") String videoUrl);
}
