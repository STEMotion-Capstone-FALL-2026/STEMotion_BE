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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Publishing an approved clip to the internal library and YouTube.
 *
 * <p>Internal-library publishing writes the canonical artifact and a legacy API
 * record atomically. YouTube is rejected until a real OAuth upload is available.
 */
@Service
@RequiredArgsConstructor
public class PublishService {

    private final PublishRecordRepository publishRepository;
    private final ClipProjectRepository projectRepository;
    private final ScriptService scriptService;
    private final AuthService authService;
    private final ClipQaService clipQaService;
    private final com.stemotion.domain.production.repository.PublicationRepository publicationRepository;
    private final ClipArtifactService clipArtifactService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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

        scriptService.requireActive(project);

        if (request.targets().contains(PublishTarget.YOUTUBE)) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "PUBLISH_TARGET_UNAVAILABLE", "YouTube OAuth publishing is not configured yet");
        }

        if (publicationRepository.existsByProjectIdAndPlatform(projectId,
                com.stemotion.domain.production.entity.Publication.Platform.INTERNAL_LIBRARY)) {
            throw ApiException.conflict("This project already has an INTERNAL_LIBRARY publication");
        }

        if (project.getVideoStatus() != VideoStatus.APPROVED) {
            throw ApiException.badRequest(
                "Clip is " + project.getVideoStatus() + "; only a QA-approved clip can be published"
            );
        }
        if (project.getVideoUrl() == null) {
            throw ApiException.badRequest("The project has no rendered video to publish");
        }
        var clip = clipArtifactService.requireCurrent(project, scriptService.currentVersion(project).getId());
        // Library exports must also produce a usable, safely escaped LMS iframe.
        VideoEmbed.build(clip.getVideoUrl());

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
                request.targets().contains(PublishTarget.LMS) ? VideoEmbed.build(clip.getVideoUrl()) : null
            )
            .build();

        record = publishRepository.save(record);
        String metadata;
        try {
            var data = new java.util.LinkedHashMap<String,Object>();
            data.put("legacyRecordId",record.getId());
            data.put("title",request.title());
            data.put("description",request.description());
            data.put("videoUrl",clip.getVideoUrl());
            data.put("lmsEmbedCode",record.getLmsEmbedCode());
            metadata=objectMapper.writeValueAsString(data);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize publication metadata",ex);
        }
        try {
            publicationRepository.saveAndFlush(com.stemotion.domain.production.entity.Publication.builder()
            .project(project).clip(clip).publisher(record.getPublishedBy())
            .platform(com.stemotion.domain.production.entity.Publication.Platform.INTERNAL_LIBRARY)
            .metadata(metadata).publishedAt(java.time.LocalDateTime.now()).build());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            if (ex.getMostSpecificCause() instanceof java.sql.SQLException cause
                    && "23505".equals(cause.getSQLState())) {
                throw ApiException.conflict("A publication for this project and platform already exists");
            }
            throw ex;
        }

        project.setVideoStatus(VideoStatus.PUBLISHED);
        projectRepository.save(project);

        return PublishResponse.from(record);
    }

}
