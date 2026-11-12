package com.stemotion.domain.production.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.PublishTarget;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.PublishRequest;
import com.stemotion.domain.production.dto.ProductionDtos.PublishResponse;
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.Publication;
import com.stemotion.domain.production.entity.PublishRecord;
import com.stemotion.domain.production.repository.PublicationRepository;
import com.stemotion.domain.production.repository.PublishRecordRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.review.dto.QaDtos.QaReport;
import com.stemotion.domain.review.service.ClipQaService;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.youtube.config.YoutubeProperties;
import com.stemotion.domain.youtube.repository.YoutubeChannelConnectionRepository;
import com.stemotion.domain.youtube.service.YoutubeUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Publishing an approved clip to the internal library and YouTube.
 */
@Service
@RequiredArgsConstructor
public class PublishService {

    private final PublishRecordRepository publishRepository;
    private final ClipProjectRepository projectRepository;
    private final ScriptService scriptService;
    private final AuthService authService;
    private final ClipQaService clipQaService;
    private final PublicationRepository publicationRepository;
    private final ClipArtifactService clipArtifactService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private com.stemotion.domain.stem.StemArtifactService stemArtifacts;
    @Autowired(required = false)
    private com.stemotion.domain.stem.StemService stem;
    @Autowired(required = false)
    private YoutubeProperties youtubeProperties;
    @Autowired(required = false)
    private YoutubeChannelConnectionRepository youtubeConnectionRepository;
    @Autowired(required = false)
    private YoutubeUploadService youtubeUploadService;

    @Transactional(readOnly = true)
    public List<PublishResponse> findByProject(String projectId) {
        scriptService.getOrThrow(projectId);
        Optional<Publication> ytPub = publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE);
        return publishRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(record -> PublishResponse.from(record, ytPub.orElse(null)))
            .toList();
    }

    @Transactional
    public PublishResponse publish(String projectId, PublishRequest request, String currentUserId) {
        ClipProject project = scriptService.getOrThrow(projectId);

        scriptService.requireActive(project);

        boolean hasYoutubeTarget = request.targets().contains(PublishTarget.YOUTUBE);
        boolean hasInternalTarget = request.targets().contains(PublishTarget.INTERNAL_LIBRARY)
                || request.targets().contains(PublishTarget.LMS)
                || !hasYoutubeTarget;

        if (hasYoutubeTarget) {
            if (youtubeProperties == null || !youtubeProperties.isEnabled()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PUBLISH_TARGET_UNAVAILABLE", "YouTube OAuth publishing is not configured yet");
            }
            String workspaceId = project.getWorkspace().getId();
            var activeConn = youtubeConnectionRepository == null ? Optional.empty()
                    : youtubeConnectionRepository.findFirstByWorkspaceIdAndRevokedAtIsNullOrderByConnectedAtDesc(workspaceId);
            if (activeConn.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "YOUTUBE_NOT_CONNECTED", "Workspace is not connected to a YouTube channel");
            }
        }

        if (stemArtifacts != null) {
            stemArtifacts.lock(project);
            var replay = stemArtifacts.publishReplay(project, currentUserId, request);
            if (replay != null) return replay;
        }

        if (hasInternalTarget && publicationRepository.existsByProjectIdAndPlatform(projectId, Publication.Platform.INTERNAL_LIBRARY)) {
            throw ApiException.conflict("This project already has an INTERNAL_LIBRARY publication");
        }

        Optional<Publication> existingYtPub = hasYoutubeTarget
                ? publicationRepository.findByProjectIdAndPlatform(projectId, Publication.Platform.YOUTUBE)
                : Optional.empty();

        if (hasYoutubeTarget && existingYtPub.isPresent()) {
            Publication existing = existingYtPub.get();
            if (!"FAILED".equals(existing.getUploadStatus())) {
                throw ApiException.conflict("A publication for this project and platform already exists");
            }
        }

        boolean isRepublishingFailedYoutube = hasYoutubeTarget && existingYtPub.isPresent()
                && "FAILED".equals(existingYtPub.get().getUploadStatus());

        if (project.getVideoStatus() != VideoStatus.APPROVED && !isRepublishingFailedYoutube) {
            throw ApiException.badRequest(
                "Clip is " + project.getVideoStatus() + "; only a QA-approved clip can be published"
            );
        }
        if (project.getVideoUrl() == null) {
            throw ApiException.badRequest("The project has no rendered video to publish");
        }
        Clip clip = clipArtifactService.requireCurrent(project, scriptService.currentVersion(project).getId());
        if (stem != null) stem.checkPublishFingerprint(project, scriptService.currentVersion(project), currentUserId);
        var artifactPublish = stemArtifacts == null ? null : stemArtifacts.beforePublish(project, clip, request);

        // Library exports must also produce a usable, safely escaped LMS iframe.
        if (request.targets().contains(PublishTarget.LMS) || hasInternalTarget) {
            VideoEmbed.build(clip.getVideoUrl());
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
                request.targets().contains(PublishTarget.LMS) ? VideoEmbed.build(clip.getVideoUrl()) : null
            )
            .build();

        record = publishRepository.save(record);
        String metadata;
        try {
            var data = new LinkedHashMap<String, Object>();
            data.put("legacyRecordId", record.getId());
            data.put("title", request.title());
            data.put("description", request.description());
            data.put("videoUrl", clip.getVideoUrl());
            data.put("lmsEmbedCode", record.getLmsEmbedCode());
            metadata = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize publication metadata", ex);
        }

        Publication publication = null;
        if (hasInternalTarget) {
            try {
                publication = publicationRepository.saveAndFlush(Publication.builder()
                    .project(project).clip(clip).publisher(record.getPublishedBy())
                    .platform(Publication.Platform.INTERNAL_LIBRARY)
                    .metadata(metadata).publishedAt(LocalDateTime.now()).build());
            } catch (DataIntegrityViolationException ex) {
                if (ex.getMostSpecificCause() instanceof SQLException cause && "23505".equals(cause.getSQLState())) {
                    throw ApiException.conflict("A publication for this project and platform already exists");
                }
                throw ex;
            }
        }

        Publication ytPublication = null;
        if (hasYoutubeTarget) {
            if (existingYtPub.isPresent()) {
                ytPublication = existingYtPub.get();
                ytPublication.setUploadStatus("PENDING");
                ytPublication.setUploadError(null);
                ytPublication.setClip(clip);
                ytPublication.setPublisher(record.getPublishedBy());
                ytPublication.setPublishedAt(LocalDateTime.now());
                ytPublication.setAttempts(ytPublication.getAttempts() + 1);
            } else {
                ytPublication = Publication.builder()
                    .project(project)
                    .clip(clip)
                    .publisher(record.getPublishedBy())
                    .platform(Publication.Platform.YOUTUBE)
                    .metadata(metadata)
                    .uploadStatus("PENDING")
                    .attempts(0)
                    .publishedAt(LocalDateTime.now())
                    .build();
            }
            ytPublication = publicationRepository.saveAndFlush(ytPublication);

            if (youtubeUploadService != null) {
                String pubId = ytPublication.getId();
                String projId = project.getId();
                String wsId = project.getWorkspace().getId();
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCommit() {
                            youtubeUploadService.uploadAsync(pubId, projId, wsId, request);
                        }
                    });
                } else {
                    youtubeUploadService.uploadAsync(pubId, projId, wsId, request);
                }
            }
        }

        Publication primaryPub = publication != null ? publication : ytPublication;
        if (stemArtifacts != null && primaryPub != null) {
            stemArtifacts.published(primaryPub, currentUserId, artifactPublish);
        }
        project.setVideoStatus(VideoStatus.PUBLISHED);
        projectRepository.save(project);

        var response = PublishResponse.from(record, ytPublication);
        if (stemArtifacts != null) stemArtifacts.rememberPublish(project, currentUserId, request, response);
        return response;
    }

}
