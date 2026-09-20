package com.stemotion.domain.project.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.project.entity.ProjectStatus;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The lifecycle from the project proposal:
 * Draft → Script Review → Approved → In Production → Clip Review → Published,
 * with a return path from Script Review back to Draft.
 */
class StatusHistoryServiceTest {

    private final StatusHistoryService service =
        new StatusHistoryService(Mockito.mock(StatusHistoryRepository.class));

    @ParameterizedTest
    @CsvSource({
        "DRAFT,          SCRIPT_REVIEW",
        "SCRIPT_REVIEW,  APPROVED",
        "SCRIPT_REVIEW,  DRAFT",
        "APPROVED,       IN_PRODUCTION",
        "IN_PRODUCTION,  CLIP_REVIEW",
        "CLIP_REVIEW,    PUBLISHED",
        "CLIP_REVIEW,    IN_PRODUCTION",
        "PUBLISHED,      ARCHIVED"
    })
    @DisplayName("allows every edge of the lifecycle")
    void allowsValidTransitions(ProjectStatus from, ProjectStatus to) {
        assertThatCode(() -> service.validateTransition(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "DRAFT,          APPROVED",
        "DRAFT,          PUBLISHED",
        "DRAFT,          IN_PRODUCTION",
        "SCRIPT_REVIEW,  PUBLISHED",
        "APPROVED,       PUBLISHED",
        "IN_PRODUCTION,  PUBLISHED",
        "PUBLISHED,      DRAFT"
    })
    @DisplayName("refuses to skip a stage of the lifecycle")
    void rejectsSkippedStages(ProjectStatus from, ProjectStatus to) {
        assertThatThrownBy(() -> service.validateTransition(from, to))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Invalid state transition");
    }

    @Test
    @DisplayName("a draft cannot be approved without a review")
    void draftCannotJumpToApproved() {
        assertThatThrownBy(() -> service.validateTransition(ProjectStatus.DRAFT, ProjectStatus.APPROVED))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("DRAFT")
            .hasMessageContaining("APPROVED");
    }

    @Test
    @DisplayName("anything can be archived")
    void archivingIsAlwaysAllowed() {
        for (ProjectStatus from : ProjectStatus.values()) {
            assertThatCode(() -> service.validateTransition(from, ProjectStatus.ARCHIVED))
                .doesNotThrowAnyException();
        }
    }
}
