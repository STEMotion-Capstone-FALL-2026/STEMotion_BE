package com.stemotion.domain.collab.service;

import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.collab.dto.CollabDtos.ContributionResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ContributionsResponse;
import com.stemotion.domain.collab.dto.CollabDtos.ContributorSummary;
import com.stemotion.domain.collab.entity.CollabEnums.ContributionType;
import com.stemotion.domain.collab.entity.ContributionEvent;
import com.stemotion.domain.collab.repository.ContributionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Records one contribution event per successful script submission, composition revision, review
 * decision, note resolution and publish, in the transaction of the action (FR-46, D18).
 */
@Service
@RequiredArgsConstructor
public class ContributionService {
    private final ContributionEventRepository events;
    private final WorkspaceAccess access;
    private final UserRepository users;

    @Transactional
    public void record(String projectId, String actorId, ContributionType type, String targetId) {
        if (projectId == null || actorId == null) return;
        String role = access.effectiveRole(projectId, actorId).map(Enum::name)
            .orElse(access.isAdmin(actorId) ? "ADMIN" : "MEMBER");
        events.save(ContributionEvent.builder().projectId(projectId).actorId(actorId).projectRole(role)
            .eventType(type).targetId(targetId).occurredAt(LocalDateTime.now()).build());
    }

    @Transactional(readOnly = true)
    public ContributionsResponse list(String projectId, String userId) {
        access.requireProject(projectId, userId, false);
        List<ContributionEvent> all = events.findByProjectIdOrderByOccurredAtDesc(projectId);
        Map<String, String> names = users.findAllById(all.stream().map(ContributionEvent::getActorId).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, u -> u.getDisplayName() == null ? u.getFullName() : u.getDisplayName()));
        Map<String, List<ContributionEvent>> byActor = all.stream()
            .collect(Collectors.groupingBy(ContributionEvent::getActorId, LinkedHashMap::new, Collectors.toList()));
        List<ContributorSummary> summary = byActor.entrySet().stream()
            .map(e -> new ContributorSummary(e.getKey(), names.get(e.getKey()), e.getValue().size(),
                e.getValue().stream().collect(Collectors.groupingBy(ev -> ev.getEventType().name(), TreeMap::new, Collectors.counting()))))
            .sorted((a, b) -> Long.compare(b.total(), a.total()))
            .toList();
        List<ContributionResponse> rows = all.stream().map(e -> ContributionResponse.of(e, names.get(e.getActorId()))).toList();
        return new ContributionsResponse(projectId, summary, rows);
    }
}
