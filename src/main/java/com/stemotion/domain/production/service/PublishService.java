package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.PublishTarget;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.PublishRequest;
import com.stemotion.domain.production.dto.ProductionDtos.PublishResponse;
import com.stemotion.domain.production.entity.PublishRecord;
import com.stemotion.domain.production.repository.PublishRecordRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.review.dto.QaDtos.QaReport;
import com.stemotion.domain.review.service.ClipQaService;
import com.stemotion.domain.script.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Publishing an approved clip to the internal library and YouTube.
 *
 * <p>The YouTube upload itself needs an OAuth-connected channel; until that
 * integration is wired, the record is created and the embed code issued while
 * {@code youtubeUrl} stays null.
 */
@Service
@RequiredArgsConstructor
public class PublishService {

    private final PublishRecordRepository publishRepository;
    private final ClipProjectRepository projectRepository;
    private final ScriptService scriptService;
    private final AuthService authService;
    private final ClipQaService clipQaService;

    @Value("${app.publish.embed-base-url}")
    private String embedBaseUrl;

    @Transactional(readOnly = true)
    public List<PublishResponse> findByProject(String projectId) {
        scriptService.getOrThrow(projectId);
        return publishRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(PublishResponse::from)
            .toList();
    }

    @Transactional
    public PublishResponse publish(String projectId, PublishRequest request, String currentUserId) {
        ClipProject project = scriptService.getOrThrow(projectId);

        if (project.getVideoStatus() != VideoStatus.APPROVED) {
            throw ApiException.badRequest(
                "Clip is " + project.getVideoStatus() + "; only a QA-approved clip can be published"
            );
        }
        if (project.getVideoUrl() == null) {
            throw ApiException.badRequest("The project has no rendered video to publish");
        }

        // Automated QA is the last gate before a clip reaches students.
        QaReport qa = clipQaService.run(projectId);
        if (!qa.passed()) {
            throw ApiException.badRequest(
                "Kiểm tra tự động chưa đạt (%d lỗi). Xem GET /api/v1/scripts/%s/qa-report."
                    .formatted(qa.failCount(), projectId)
            );
        }

        String targets = request.targets().stream()
            .map(Enum::name)
            .collect(Collectors.joining(","));

        PublishRecord record = PublishRecord.builder()
            .project(project)
            .publishedBy(authService.findUserOrThrow(currentUserId))
            .targets(targets)
            .title(request.title())
            .description(request.description())
            .lmsEmbedCode(
                request.targets().contains(PublishTarget.LMS) ? buildEmbedCode(project.getId()) : null
            )
            .build();

        record = publishRepository.save(record);

        project.setVideoStatus(VideoStatus.PUBLISHED);
        projectRepository.save(project);

        return PublishResponse.from(record);
    }

    private String buildEmbedCode(String projectId) {
        return "<iframe src=\"%s/%s\" width=\"100%%\" height=\"540\" frameborder=\"0\" allowfullscreen></iframe>"
            .formatted(embedBaseUrl, projectId);
    }
}
