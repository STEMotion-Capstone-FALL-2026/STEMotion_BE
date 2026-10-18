package com.stemotion.domain.review.service;

import com.stemotion.domain.production.repository.ClipRepository;
import com.stemotion.domain.production.repository.CompositionRepository;
import com.stemotion.domain.project.entity.StatusHistory;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import com.stemotion.domain.review.dto.FeedbackDtos.ReviewHistoryEntry;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reviewer decisions of a project, newest first (FE checklist BE-P0-4): who approved or returned
 * which script version, clip or composition revision, and why. Read from the append-only
 * status history the decisions are recorded in.
 */
@Service
@RequiredArgsConstructor
public class ReviewHistoryService {

    private static final Pattern REVISION = Pattern.compile("compositionRevision=(\\d+)");

    private final StatusHistoryRepository histories;
    private final ScriptService scriptService;
    private final ScriptVersionRepository versions;
    private final ClipRepository clips;
    private final CompositionRepository compositions;
    private final WorkspaceMemberRepository members;

    @Transactional(readOnly = true)
    public List<ReviewHistoryEntry> findByProject(String projectId) {
        var project = scriptService.getOrThrow(projectId);
        String workspaceId = project.getWorkspace().getId();
        return histories.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(event -> event.getEventType() == StatusHistory.EventType.REVIEW_DECISION)
            .map(event -> toEntry(event, workspaceId))
            .toList();
    }

    private ReviewHistoryEntry toEntry(StatusHistory event, String workspaceId) {
        var actor = event.getChangedBy();
        String role = members.findByWorkspaceIdAndUserId(workspaceId, actor.getId())
            .map(member -> member.getRole().name())
            .orElse(actor.getRole().name());
        Integer versionNo = null;
        String clipId = null;
        Integer revision = null;
        switch (event.getTargetType()) {
            case SCRIPT -> versionNo = versions.findById(event.getTargetId())
                .map(version -> version.getVersionNo()).orElse(null);
            case CLIP -> {
                clipId = event.getTargetId();
                var clip = clips.findById(event.getTargetId()).orElse(null);
                if (clip != null && clip.getRenderJob() != null) {
                    versionNo = clip.getRenderJob().getScriptVersion() == null ? null
                        : clip.getRenderJob().getScriptVersion().getVersionNo();
                    revision = clip.getRenderJob().getCompositionRevision();
                }
            }
            case COMPOSITION -> {
                versionNo = compositions.findById(event.getTargetId())
                    .map(composition -> composition.getScriptVersion().getVersionNo()).orElse(null);
                if (event.getNote() != null) {
                    Matcher matcher = REVISION.matcher(event.getNote());
                    if (matcher.find()) revision = Integer.valueOf(matcher.group(1));
                }
            }
            default -> { }
        }
        return new ReviewHistoryEntry(
            event.getId(),
            actor.getFullName(),
            role,
            event.getDecision() == StatusHistory.Decision.APPROVED ? "APPROVE" : "REJECT",
            event.getTargetType().name(),
            versionNo,
            clipId,
            revision,
            event.getReason(),
            event.getCreatedAt()
        );
    }
}
