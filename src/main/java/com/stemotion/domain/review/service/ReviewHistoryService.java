package com.stemotion.domain.review.service;

import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.Composition;
import com.stemotion.domain.production.repository.ClipRepository;
import com.stemotion.domain.production.repository.CompositionRepository;
import com.stemotion.domain.project.entity.StatusHistory;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import com.stemotion.domain.review.dto.FeedbackDtos.ReviewHistoryEntry;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reviewer decisions of a project, newest first (FE checklist BE-P0-4): who approved or returned
 * which script version, clip or composition revision, and why. Read from the append-only
 * status history the decisions are recorded in.
 *
 * <p>The decisions, the workspace's members and each kind of target are read with one query each
 * (DB performance plan 2.5), however many decisions there are.
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
        List<StatusHistory> decisions = histories.findEventsWithActor(projectId, StatusHistory.EventType.REVIEW_DECISION);
        if (decisions.isEmpty()) return List.of();

        Map<String, String> roles = members.findWithUserByWorkspaceId(project.getWorkspace().getId()).stream()
            .collect(Collectors.toMap(m -> m.getUser().getId(), m -> m.getRole().name(), (a, b) -> a));
        Map<String, ScriptVersion> versionsById = byId(targets(decisions, StatusHistory.TargetType.SCRIPT),
            ids -> versions.findAllById(ids), ScriptVersion::getId);
        Map<String, Clip> clipsById = byId(targets(decisions, StatusHistory.TargetType.CLIP),
            clips::findWithRenderJobByIdIn, Clip::getId);
        Map<String, Composition> compositionsById = byId(targets(decisions, StatusHistory.TargetType.COMPOSITION),
            compositions::findWithVersionByIdIn, Composition::getId);

        return decisions.stream()
            .map(event -> toEntry(event, roles, versionsById, clipsById, compositionsById))
            .toList();
    }

    private static Set<String> targets(List<StatusHistory> decisions, StatusHistory.TargetType type) {
        return decisions.stream().filter(e -> e.getTargetType() == type)
            .map(StatusHistory::getTargetId).collect(Collectors.toSet());
    }

    private static <T> Map<String, T> byId(Set<String> ids, Function<Collection<String>, ? extends Iterable<T>> load,
                                           Function<T, String> id) {
        if (ids.isEmpty()) return Map.of();
        Map<String, T> map = new java.util.HashMap<>();
        for (T item : load.apply(ids)) map.put(id.apply(item), item);
        return map;
    }

    private ReviewHistoryEntry toEntry(StatusHistory event, Map<String, String> roles,
                                       Map<String, ScriptVersion> versionsById, Map<String, Clip> clipsById,
                                       Map<String, Composition> compositionsById) {
        var actor = event.getChangedBy();
        String role = roles.getOrDefault(actor.getId(), actor.getRole().name());
        Integer versionNo = null;
        String clipId = null;
        Integer revision = null;
        switch (event.getTargetType()) {
            case SCRIPT -> {
                ScriptVersion version = versionsById.get(event.getTargetId());
                versionNo = version == null ? null : version.getVersionNo();
            }
            case CLIP -> {
                clipId = event.getTargetId();
                Clip clip = clipsById.get(event.getTargetId());
                if (clip != null && clip.getRenderJob() != null) {
                    versionNo = clip.getRenderJob().getScriptVersion() == null ? null
                        : clip.getRenderJob().getScriptVersion().getVersionNo();
                    revision = clip.getRenderJob().getCompositionRevision();
                }
            }
            case COMPOSITION -> {
                Composition composition = compositionsById.get(event.getTargetId());
                versionNo = composition == null ? null : composition.getScriptVersion().getVersionNo();
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
