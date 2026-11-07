package com.stemotion.domain.library.service;

import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.library.dto.LibraryDtos.LibraryItem;
import com.stemotion.domain.production.entity.PublishRecord;
import com.stemotion.domain.production.repository.PublishRecordRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stemotion.domain.production.entity.Publication;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The internal media library: everything that reached PUBLISHED, with the
 * export links a teacher needs to drop a clip into an LMS.
 */
@Service
@RequiredArgsConstructor
public class LibraryService {

    private final ClipProjectRepository projectRepository;
    private final PublishRecordRepository publishRepository;
    private final SceneRepository sceneRepository;
    private final ScriptService scriptService;
    private final com.stemotion.domain.production.repository.PublicationRepository publicationRepository;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.stemotion.domain.stem.StemArtifactService stemArtifacts;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private StemLibraryQuery stemLibrary;

    @Transactional(readOnly=true)
    public List<LibraryItem> search(StemLibraryQuery.Filters filters,com.stemotion.core.web.PageParams page){
        if(stemLibrary!=null)return stemLibrary.find(filters,page);
        if(filters.extended())throw ApiException.badRequest("STEM library projection is not enabled");
        return page==null?findPublished(filters.subject(),filters.grade()):findPublished(filters.subject(),filters.grade(),page).getContent();
    }
    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<LibraryItem> searchPage(StemLibraryQuery.Filters filters,com.stemotion.core.web.PageParams page){
        if(stemLibrary!=null)return new org.springframework.data.domain.PageImpl<>(stemLibrary.find(filters,page),page.pageable(),stemLibrary.count(filters));
        if(filters.extended())throw ApiException.badRequest("STEM library projection is not enabled");return findPublished(filters.subject(),filters.grade(),page);
    }
    @Transactional(readOnly=true)
    public Map<String,List<String>> facets(StemLibraryQuery.Filters filters){
        if(stemLibrary!=null)return stemLibrary.facets(filters);
        if(filters.extended())throw ApiException.badRequest("STEM library projection is not enabled");
        var old=availableFilters();return Map.of("subjects",old.subjects().stream().map(Enum::name).toList(),"gradeLevels",old.gradeLevels());
    }

    /** Subjects and grades present in the library, for the filter dropdowns. */
    public record LibraryFilters(List<StemSubject> subjects, List<String> gradeLevels) {}

    /**
     * Filtering happens in memory rather than in JPQL: a null enum parameter
     * reaches Postgres untyped and breaks lower(?), and the published library
     * is small enough that one indexed status query is cheaper than the
     * workarounds.
     */
    @Transactional(readOnly = true)
    public List<LibraryItem> findPublished(StemSubject subject, String gradeLevel) {
        String grade = (gradeLevel == null || gradeLevel.isBlank()) ? null : gradeLevel.trim();

        if(stemLibrary!=null)return stemLibrary.find(StemLibraryQuery.Filters.simple(subject,grade),null);
        if(stemArtifacts!=null)return snapshotItems(subject,grade);
        return toItems(library(subject, grade, org.springframework.data.domain.Pageable.unpaged()).getContent());
    }

    /**
     * One page of the library. Unfiltered pages are cut in SQL; a subject or grade filter is still
     * applied in memory (see the note above) and the filtered list is then paged.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<LibraryItem> findPublished(StemSubject subject, String gradeLevel,
            com.stemotion.core.web.PageParams paging) {
        String grade = (gradeLevel == null || gradeLevel.isBlank()) ? null : gradeLevel.trim();
        if(stemLibrary!=null)return new org.springframework.data.domain.PageImpl<>(stemLibrary.find(StemLibraryQuery.Filters.simple(subject,grade),paging),paging.pageable(),stemLibrary.count(StemLibraryQuery.Filters.simple(subject,grade)));
        if(stemArtifacts!=null){
            var all=snapshotItems(subject,grade);var pageable=paging.pageable();int from=(int)Math.min(pageable.getOffset(),all.size());int to=Math.min(from+pageable.getPageSize(),all.size());
            return new org.springframework.data.domain.PageImpl<>(all.subList(from,to),pageable,all.size());
        }
        var page = library(subject, grade, paging.pageable());
        return new org.springframework.data.domain.PageImpl<>(toItems(page.getContent()), page.getPageable(),
            page.getTotalElements());
    }

    /** Library entries with the optional filters applied in SQL, cut by the given page. */
    private org.springframework.data.domain.Page<Publication> library(StemSubject subject, String grade,
            org.springframework.data.domain.Pageable pageable) {
        return publicationRepository.findLibrary(Publication.Platform.INTERNAL_LIBRARY, VideoStatus.PUBLISHED,
            subject == null, subject == null ? StemSubject.Physics : subject,
            grade == null, grade == null ? "" : grade.toLowerCase(java.util.Locale.ROOT), pageable);
    }


    @Transactional(readOnly = true)
    public LibraryItem findOne(String projectId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        if (Boolean.TRUE.equals(project.getArchived())) {
            throw ApiException.notFound("Library item", projectId);
        }
        if (project.getVideoStatus() != VideoStatus.PUBLISHED) {
            throw ApiException.badRequest("Clip chưa được xuất bản nên không có trong thư viện");
        }
        if(stemLibrary!=null)return stemLibrary.findOne(projectId);
        Publication entry = publicationRepository
            .findLibraryEntry(projectId, Publication.Platform.INTERNAL_LIBRARY, VideoStatus.PUBLISHED)
            .orElseThrow(() -> ApiException.conflict("No artifact-backed INTERNAL_LIBRARY publication exists"));
        return toItems(List.of(entry)).get(0);
    }

    /**
     * Only offers filters that would actually return something, so the UI
     * never shows a dropdown entry that leads to an empty list.
     */
    @Transactional(readOnly = true)
    public LibraryFilters availableFilters() {
        if(stemArtifacts!=null){
            var items=snapshotItems(null,null);
            return new LibraryFilters(items.stream().map(LibraryItem::subject).distinct().sorted(Comparator.comparing(Enum::name)).toList(),items.stream().map(LibraryItem::gradeLevel).filter(java.util.Objects::nonNull).distinct().sorted().toList());
        }
        // Two DISTINCT queries over the columns, not the library rows (DB performance plan, follow-up).
        List<StemSubject> subjects = publicationRepository
            .findLibrarySubjects(Publication.Platform.INTERNAL_LIBRARY, VideoStatus.PUBLISHED).stream()
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(Enum::name))
            .toList();

        List<String> grades = publicationRepository
            .findLibraryGrades(Publication.Platform.INTERNAL_LIBRARY, VideoStatus.PUBLISHED).stream()
            .filter(g -> !g.isBlank())
            .distinct()
            .sorted()
            .toList();

        return new LibraryFilters(subjects, grades);
    }

    /** Items for several entries: their latest publish record and scene count, one query each. */
    private List<LibraryItem> toItems(List<Publication> entries) {
        if (entries.isEmpty()) return List.of();
        List<String> ids = entries.stream().map(e -> e.getProject().getId()).toList();
        Map<String, PublishRecord> latest = new HashMap<>();
        for (PublishRecord record : publishRepository.findLatestByProjectIds(ids)) {
            latest.put(record.getProject().getId(), record);
        }
        Map<String, Long> scenes = new HashMap<>();
        for (Object[] row : projectRepository.sceneCounts(ids)) {
            scenes.put(String.valueOf(row[0]), row[1] == null ? 0L : ((Number) row[1]).longValue());
        }
        return entries.stream().map(e -> toItem(e, latest.get(e.getProject().getId()),
            scenes.getOrDefault(e.getProject().getId(), 0L).intValue())).toList();
    }

    private List<LibraryItem> snapshotItems(StemSubject subject,String grade){
        return toItems(library(null,null,org.springframework.data.domain.Pageable.unpaged()).getContent()).stream()
            .filter(i->subject==null||i.subject()==subject).filter(i->grade==null||grade.equalsIgnoreCase(i.gradeLevel())).toList();
    }

    private LibraryItem toItem(Publication publication, PublishRecord latest, int sceneCount) {
        ClipProject project = publication.getProject();
        String videoUrl = publication.getClip().getVideoUrl();
        if(stemArtifacts!=null){
            var meta=stemArtifacts.publicationMetadata(publication.getId());
            if(meta.isObject()){
                com.fasterxml.jackson.databind.JsonNode export;
                try{export=new com.fasterxml.jackson.databind.ObjectMapper().readTree(publication.getMetadata());}catch(Exception ex){throw ApiException.conflict("Unreadable publication snapshot metadata");}
                return new LibraryItem(project.getId(),meta.path("title").asText(),StemSubject.valueOf(meta.path("subject").asText()),meta.path("gradeLevel").asText(),meta.path("totalDurationSeconds").asInt(),meta.path("sceneCount").asInt(),meta.path("ownerName").asText(),meta.path("workspaceName").asText(),videoUrl,export.path("lmsEmbedCode").isTextual()?export.path("lmsEmbedCode").asText():com.stemotion.domain.production.service.VideoEmbed.build(videoUrl),null,publication.getPublishedAt(),meta.path("verificationStatus").asText("UNVERIFIED"),meta.path("snapshotId").asText());
            }
        }
        return new LibraryItem(
            project.getId(),
            project.getTitle(),
            project.getSubject(),
            project.getGradeLevel(),
            project.getTotalDurationSeconds(),
            sceneCount,
            project.getOwner().getFullName(),
            project.getWorkspace().getName(),
            videoUrl,
            latest != null && latest.getLmsEmbedCode() != null
                ? latest.getLmsEmbedCode()
                : com.stemotion.domain.production.service.VideoEmbed.build(videoUrl),
            latest == null ? null : latest.getYoutubeUrl(),
            latest == null ? project.getUpdatedAt() : latest.getCreatedAt()
        );
    }
}
