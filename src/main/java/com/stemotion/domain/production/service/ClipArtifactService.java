package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.ClipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.stemotion.domain.project.entity.ClipProject;

@Service
@RequiredArgsConstructor
public class ClipArtifactService {
    private final ClipRepository clipRepository;

    @Transactional
    public Clip recordCompleted(RenderJob job) {
        if (job.getStatus() != RenderStatus.COMPLETED
                || job.getVideoUrl() == null || job.getVideoUrl().isBlank()) {
            throw ApiException.badRequest("Only a completed render with a video URL creates a Clip");
        }
        return clipRepository.findByRenderJobId(job.getId()).map(existing -> {
            if (!existing.getVideoUrl().equals(job.getVideoUrl())) {
                throw ApiException.conflict("A completed render artifact cannot change its video URL");
            }
            return existing;
        }).orElseGet(() -> clipRepository.save(Clip.builder()
            .renderJob(job).videoUrl(job.getVideoUrl()).build()));
    }

    @Transactional(readOnly = true)
    public Clip requireCurrent(ClipProject project, String scriptVersionId) {
        var artifacts = clipRepository.findCurrentArtifacts(project.getId(), scriptVersionId, project.getVideoUrl());
        if (artifacts.isEmpty()) {
            throw ApiException.conflict("No completed Clip matches the current script version and video URL");
        }
        // Same URL from distinct jobs is ambiguous, not evidence of which artifact was reviewed.
        if (artifacts.size() != 1) {
            throw ApiException.conflict("Multiple Clip artifacts match this video; select an artifact explicitly");
        }
        return artifacts.get(0);
    }
}
