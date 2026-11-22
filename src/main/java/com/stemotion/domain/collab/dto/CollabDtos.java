package com.stemotion.domain.collab.dto;

import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.domain.collab.entity.AiProviderSettings;
import com.stemotion.domain.collab.entity.ContributionEvent;
import com.stemotion.domain.collab.entity.EditLease;
import com.stemotion.domain.collab.entity.ProjectJoinRequest;
import com.stemotion.domain.collab.entity.ProjectMember;
import com.stemotion.domain.collab.entity.ProjectProposal;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Requests and responses of the collaboration API (UC-29 to UC-34, Report 4 sections 3.10 to 3.12). */
public final class CollabDtos {
    private CollabDtos() {}

    public record ProposeRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull StemSubject subject,
        @Size(max = 32) String gradeLevel) {}

    public record DecisionRequest(@Size(max = 2000) String reason) {}

    public record ProposalResponse(String id, String workspaceId, String proposerId, String proposerName, String title,
                                   StemSubject subject, String gradeLevel, String status, String decisionReason,
                                   String decidedBy, LocalDateTime decidedAt, String projectId, LocalDateTime createdAt) {
        public static ProposalResponse of(ProjectProposal p, String proposerName) {
            return new ProposalResponse(p.getId(), p.getWorkspaceId(), p.getProposerId(), proposerName, p.getTitle(),
                p.getSubject(), p.getGradeLevel(), p.getStatus().name(), p.getDecisionReason(), p.getDecidedBy(),
                p.getDecidedAt(), p.getProjectId(), p.getCreatedAt());
        }
    }

    public record JoinRequest(@NotNull WorkspaceRole role) {}

    public record JoinRequestResponse(String id, String projectId, String projectTitle, String userId, String userName,
                                      WorkspaceRole role, String status, String reason, String decidedBy,
                                      LocalDateTime decidedAt, LocalDateTime createdAt) {
        public static JoinRequestResponse of(ProjectJoinRequest r, String projectTitle, String userName) {
            return new JoinRequestResponse(r.getId(), r.getProjectId(), projectTitle, r.getUserId(), userName, r.getRole(),
                r.getStatus().name(), r.getReason(), r.getDecidedBy(), r.getDecidedAt(), r.getCreatedAt());
        }
    }

    public record MemberResponse(String userId, String name, WorkspaceRole role, String status, String revokedBy,
                                 LocalDateTime revokedAt, LocalDateTime joinedAt) {
        public static MemberResponse of(ProjectMember m, String name) {
            return new MemberResponse(m.getUserId(), name, m.getRole(), m.getStatus().name(), m.getRevokedBy(),
                m.getRevokedAt(), m.getCreatedAt());
        }
    }

    /** usesProjectRoles = false: the project keeps workspace roles (created before V28). */
    public record ProjectTeamResponse(String projectId, boolean usesProjectRoles, List<MemberResponse> members,
                                      List<String> missingRoles) {}

    public record LeaseResponse(String resourceType, String projectId, String holderId, String holderName,
                                LocalDateTime acquiredAt, LocalDateTime expiresAt, boolean held, boolean mine,
                                boolean required) {
        public static LeaseResponse of(String type, String projectId, EditLease lease, String holderName, String me,
                                       LocalDateTime now, boolean required) {
            boolean held = lease != null && lease.validAt(now);
            return new LeaseResponse(type, projectId, held ? lease.getHolderId() : null, held ? holderName : null,
                held ? lease.getAcquiredAt() : null, held ? lease.getExpiresAt() : null, held,
                held && lease.getHolderId().equals(me), required);
        }
    }

    public record ContributionResponse(String id, String actorId, String actorName, String projectRole, String eventType,
                                       String targetId, LocalDateTime occurredAt) {
        public static ContributionResponse of(ContributionEvent e, String actorName) {
            return new ContributionResponse(e.getId(), e.getActorId(), actorName, e.getProjectRole(),
                e.getEventType().name(), e.getTargetId(), e.getOccurredAt());
        }
    }

    public record ContributorSummary(String actorId, String actorName, long total, java.util.Map<String, Long> byType) {}

    public record ContributionsResponse(String projectId, List<ContributorSummary> byMember, List<ContributionResponse> events) {}

    public record BoardCard(String projectId, String title, String workspaceId, String stage, String ownerId,
                            String ownerName, List<String> blockers) {}

    public record LeaderDashboardResponse(List<ProposalResponse> pendingProposals, List<JoinRequestResponse> pendingJoinRequests,
                                          java.util.Map<String, List<BoardCard>> board) {}

    public record AiSettingsRequest(
        @NotNull Integer baseRevision,
        List<String> allowedAiProviders,
        List<String> allowedModels,
        List<String> allowedVoices,
        @NotNull @DecimalMin("0.00") BigDecimal monthlyBudgetUsd,
        @NotNull @DecimalMin("0.00") BigDecimal perClipCapUsd) {}

    public record AiSettingsResponse(List<String> allowedAiProviders, List<String> allowedModels, List<String> allowedVoices,
                                     BigDecimal monthlyBudgetUsd, BigDecimal perClipCapUsd, int revision, String updatedBy,
                                     LocalDateTime updatedAt, BigDecimal spentThisMonthUsd) {
        public static AiSettingsResponse of(AiProviderSettings s, BigDecimal spent) {
            return new AiSettingsResponse(split(s.getAllowedAiProviders()), split(s.getAllowedModels()),
                split(s.getAllowedVoices()), s.getMonthlyBudgetUsd(), s.getPerClipCapUsd(), s.getRevision(),
                s.getUpdatedBy(), s.getUpdatedAt(), spent);
        }
    }

    public static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }

    public static String join(List<String> values) {
        if (values == null) return "";
        return String.join(",", values.stream().map(v -> v == null ? "" : v.trim()).filter(v -> !v.isEmpty())
            .map(v -> v.replace(",", "")).distinct().toList());
    }
}
