package com.stemotion.domain.production.repository;

import com.stemotion.domain.production.entity.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PublicationRepository extends JpaRepository<Publication,String> {
    Optional<Publication> findByProjectIdAndPlatform(String projectId,Publication.Platform platform);
    boolean existsByProjectIdAndPlatform(String projectId,Publication.Platform platform);

    /** Library rows: publication, clip, project, owner and workspace in one query, newest project first. */
    String LIBRARY = "select pub from Publication pub join fetch pub.clip join fetch pub.project p "
        + "join fetch p.owner join fetch p.workspace where pub.platform = :platform and p.videoStatus = :published "
        + "and (p.archived is null or p.archived = false) ";

    @org.springframework.data.jpa.repository.Query(LIBRARY + "order by p.createdAt desc")
    java.util.List<Publication> findLibrary(@org.springframework.data.repository.query.Param("platform") Publication.Platform platform,
        @org.springframework.data.repository.query.Param("published") com.stemotion.core.enums.StemEnums.VideoStatus published);

    @org.springframework.data.jpa.repository.Query(value = LIBRARY + "order by p.createdAt desc",
        countQuery = "select count(pub) from Publication pub join pub.project p where pub.platform = :platform "
            + "and p.videoStatus = :published and (p.archived is null or p.archived = false)")
    org.springframework.data.domain.Page<Publication> findLibrary(@org.springframework.data.repository.query.Param("platform") Publication.Platform platform,
        @org.springframework.data.repository.query.Param("published") com.stemotion.core.enums.StemEnums.VideoStatus published, org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query(LIBRARY + "and p.id = :projectId")
    Optional<Publication> findLibraryEntry(@org.springframework.data.repository.query.Param("projectId") String projectId,
        @org.springframework.data.repository.query.Param("platform") Publication.Platform platform,
        @org.springframework.data.repository.query.Param("published") com.stemotion.core.enums.StemEnums.VideoStatus published);
}
