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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

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

    @Value("${app.publish.embed-base-url}")
    private String embedBaseUrl;

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

        return publishedProjects().stream()
            .filter(p -> subject == null || p.getSubject() == subject)
            .filter(p -> grade == null
                || (p.getGradeLevel() != null && p.getGradeLevel().equalsIgnoreCase(grade)))
            .map(this::toItem)
            .toList();
    }

    private List<ClipProject> publishedProjects() {
        return projectRepository.findByVideoStatusOrderByCreatedAtDesc(VideoStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public LibraryItem findOne(String projectId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        if (project.getVideoStatus() != VideoStatus.PUBLISHED) {
            throw ApiException.badRequest("Clip chưa được xuất bản nên không có trong thư viện");
        }
        return toItem(project);
    }

    /**
     * Only offers filters that would actually return something, so the UI
     * never shows a dropdown entry that leads to an empty list.
     */
    @Transactional(readOnly = true)
    public LibraryFilters availableFilters() {
        List<ClipProject> published = publishedProjects();

        List<StemSubject> subjects = published.stream()
            .map(ClipProject::getSubject)
            .distinct()
            .sorted(Comparator.comparing(Enum::name))
            .toList();

        List<String> grades = published.stream()
            .map(ClipProject::getGradeLevel)
            .filter(g -> g != null && !g.isBlank())
            .distinct()
            .sorted()
            .toList();

        return new LibraryFilters(subjects, grades);
    }

    private LibraryItem toItem(ClipProject project) {
        PublishRecord latest = publishRepository
            .findByProjectIdOrderByCreatedAtDesc(project.getId())
            .stream()
            .findFirst()
            .orElse(null);

        int sceneCount = sceneRepository
            .findByScriptVersionIdOrderByOrderNoAsc(scriptService.currentVersion(project).getId())
            .size();

        return new LibraryItem(
            project.getId(),
            project.getTitle(),
            project.getSubject(),
            project.getGradeLevel(),
            project.getTotalDurationSeconds(),
            sceneCount,
            project.getOwner().getFullName(),
            project.getWorkspace().getName(),
            project.getVideoUrl(),
            latest != null && latest.getLmsEmbedCode() != null
                ? latest.getLmsEmbedCode()
                : buildEmbedCode(project.getId()),
            latest == null ? null : latest.getYoutubeUrl(),
            latest == null ? project.getUpdatedAt() : latest.getCreatedAt()
        );
    }

    private String buildEmbedCode(String projectId) {
        return "<iframe src=\"%s/%s\" width=\"100%%\" height=\"540\" frameborder=\"0\" allowfullscreen></iframe>"
            .formatted(embedBaseUrl, projectId);
    }
}
