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
        LocalDateTime createdAt
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
                comment.getCreatedAt()
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
