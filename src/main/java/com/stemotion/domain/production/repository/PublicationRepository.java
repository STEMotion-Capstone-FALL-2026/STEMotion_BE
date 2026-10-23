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

    /**
     * Same rows as LIBRARY (the clip join excludes publications without a clip) with optional
     * subject / grade filters. Flags stand in for the optional filters, so no null enum parameter
     * reaches Postgres (the reason filtering used to run in memory).
     */
    String LIBRARY_FROM = "from Publication pub join pub.clip clip join pub.project p "
        + "where pub.platform = :platform and p.videoStatus = :published and (p.archived is null or p.archived = false) "
        + "and (:anySubject = true or p.subject = :subject) "
        + "and (:anyGrade = true or lower(p.gradeLevel) = :grade) ";

    @org.springframework.data.jpa.repository.Query(value = "select pub from Publication pub join fetch pub.clip join fetch pub.project p "
            + "join fetch p.owner join fetch p.workspace "
            + "where pub.platform = :platform and p.videoStatus = :published and (p.archived is null or p.archived = false) "
            + "and (:anySubject = true or p.subject = :subject) "
            + "and (:anyGrade = true or lower(p.gradeLevel) = :grade) order by p.createdAt desc",
        countQuery = "select count(pub) " + LIBRARY_FROM)
    org.springframework.data.domain.Page<Publication> findLibrary(@org.springframework.data.repository.query.Param("platform") Publication.Platform platform,
        @org.springframework.data.repository.query.Param("published") com.stemotion.core.enums.StemEnums.VideoStatus published,
        @org.springframework.data.repository.query.Param("anySubject") boolean anySubject,
        @org.springframework.data.repository.query.Param("subject") com.stemotion.core.enums.StemEnums.StemSubject subject,
        @org.springframework.data.repository.query.Param("anyGrade") boolean anyGrade, @org.springframework.data.repository.query.Param("grade") String grade, org.springframework.data.domain.Pageable page);

    /** Subjects present in the library, without loading the rows. */
    @org.springframework.data.jpa.repository.Query("select distinct p.subject from Publication pub join pub.clip clip join pub.project p "
        + "where pub.platform = :platform and p.videoStatus = :published and (p.archived is null or p.archived = false)")
    java.util.List<com.stemotion.core.enums.StemEnums.StemSubject> findLibrarySubjects(
        @org.springframework.data.repository.query.Param("platform") Publication.Platform platform,
        @org.springframework.data.repository.query.Param("published") com.stemotion.core.enums.StemEnums.VideoStatus published);

    /** Grade levels present in the library, without loading the rows. */
    @org.springframework.data.jpa.repository.Query("select distinct p.gradeLevel from Publication pub join pub.clip clip join pub.project p "
        + "where pub.platform = :platform and p.videoStatus = :published and (p.archived is null or p.archived = false) "
        + "and p.gradeLevel is not null")
    java.util.List<String> findLibraryGrades(@org.springframework.data.repository.query.Param("platform") Publication.Platform platform,
        @org.springframework.data.repository.query.Param("published") com.stemotion.core.enums.StemEnums.VideoStatus published);

    @org.springframework.data.jpa.repository.Query(LIBRARY + "and p.id = :projectId")
    Optional<Publication> findLibraryEntry(@org.springframework.data.repository.query.Param("projectId") String projectId,
        @org.springframework.data.repository.query.Param("platform") Publication.Platform platform,
        @org.springframework.data.repository.query.Param("published") com.stemotion.core.enums.StemEnums.VideoStatus published);
}
