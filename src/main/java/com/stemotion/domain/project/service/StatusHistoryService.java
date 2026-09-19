package com.stemotion.domain.project.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.entity.ProjectStatus;
import com.stemotion.domain.project.entity.StatusHistory;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Audit trail of pipeline transitions, plus the transition table that decides
 * which moves are legal.
 *
 * <p>The pipeline state is derived from the project's script and video status,
 * so this service records what actually happened rather than owning the state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusHistoryService {

    private final StatusHistoryRepository statusHistoryRepository;

    @Transactional(readOnly = true)
    public List<StatusHistory> findByProject(String projectId) {
        return statusHistoryRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * Rejects a move that is not an edge of the lifecycle described in the
     * proposal: Draft → Script Review → Approved → In Production → Clip Review
     * → Published, with a return path from Script Review back to Draft.
     */
    public void validateTransition(ProjectStatus current, ProjectStatus target) {
        boolean valid = switch (target) {
            case DRAFT -> current == ProjectStatus.SCRIPT_REVIEW || current == ProjectStatus.DRAFT;
            case SCRIPT_REVIEW -> current == ProjectStatus.DRAFT;
            case APPROVED -> current == ProjectStatus.SCRIPT_REVIEW;
            case IN_PRODUCTION -> current == ProjectStatus.APPROVED || current == ProjectStatus.CLIP_REVIEW;
            case CLIP_REVIEW -> current == ProjectStatus.IN_PRODUCTION;
            case PUBLISHED -> current == ProjectStatus.CLIP_REVIEW;
            case ARCHIVED -> true;
        };

        if (!valid) {
            throw ApiException.badRequest(
                "Invalid state transition: %s -> %s".formatted(current, target)
            );
        }
    }

    /** Records a transition that has already been applied to the project. */
    @Transactional
    public void record(ClipProject project, ProjectStatus from, User changedBy, String note) {
        ProjectStatus to = project.getPipelineStatus();
        statusHistoryRepository.save(StatusHistory.builder()
            .project(project)
            .fromStatus(from)
            .toStatus(to)
            .note(note)
            .changedBy(changedBy)
            .build());

        log.info("Project [{}] moved {} -> {} by [{}]", project.getId(), from, to, changedBy.getId());
    }
}
