package com.stemotion.domain.review.dto;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.review.entity.FeedbackComment;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class FeedbackDtos {

    private FeedbackDtos() {}

    /**
     * Either a clip pin (timestampSec set) or an inline script note (sceneId set).
     */
    public record FeedbackRequest(
        Double timestampSec,
        String sceneId,

        @NotBlank(message = "content is required")
        String content,

        String tag
    ) {}

    /** A reply under a review note (BE-P1-2). */
    public record ReplyRequest(
        @NotBlank(message = "content is required")
        String content
    ) {}

    /**
     * One reviewer decision from the status history (BE-P0-4). action is APPROVE or REJECT;
     * targetType SCRIPT (a script version), CLIP (a rendered MP4) or COMPOSITION (a composition
     * revision reviewed in the web player).
     */
    public record ReviewHistoryEntry(
        String id,
        String actorName,
        String actorRole,
        String action,
        String targetType,
        Integer scriptVersionNo,
        String clipId,
        Integer compositionRevision,
        String note,
        LocalDateTime createdAt
    ) {}

    /** Mirrors the front end's FeedbackComment interface. */
    public record FeedbackResponse(
        String id,
        String author,
        String avatar,
        User.UserRole role,
        Double timestampSec,
        String sceneId,
        String content,
        String tag,
        FeedbackStatus status,
        LocalDateTime createdAt,
        /** The rendered Clip the note was written on; null for script and web-player notes. */
        String clipId,
        /** Set on a reply: the note it answers. */
        String parentId,
        String resolvedByName,
        LocalDateTime resolvedAt
    ) {
        public static FeedbackResponse from(FeedbackComment comment) {
            User author = comment.getAuthor();
            return new FeedbackResponse(
                comment.getId(),
                author.getFullName(),
                initialsOf(author.getFullName()),
                author.getRole(),
                comment.getTimestampSec(),
                comment.getScene() == null ? null : comment.getScene().getId(),
                comment.getContent(),
                comment.getTag(),
                comment.getStatus(),
                comment.getCreatedAt(),
                comment.getClipId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getResolvedBy() == null ? null : comment.getResolvedBy().getFullName(),
                comment.getResolvedAt()
            );
        }

        /** The UI shows initials when a user has no uploaded avatar. */
        private static String initialsOf(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return "?";
            }
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, 1).toUpperCase();
            }
            return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
    }
}
