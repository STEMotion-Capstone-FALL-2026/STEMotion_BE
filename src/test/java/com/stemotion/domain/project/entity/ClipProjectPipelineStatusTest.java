package com.stemotion.domain.project.entity;

import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pipeline state is derived rather than stored, so the mapping from the
 * (scriptStatus, videoStatus) pair is pinned down here.
 */
class ClipProjectPipelineStatusTest {

    private static ClipProject project(ScriptStatus script, VideoStatus video) {
        return ClipProject.builder()
            .scriptStatus(script)
            .videoStatus(video)
            .archived(false)
            .build();
    }

    @Test
    @DisplayName("a fresh draft reads as DRAFT")
    void draft() {
        assertThat(project(ScriptStatus.DRAFT, VideoStatus.NOT_RENDERED).getPipelineStatus())
            .isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    @DisplayName("a returned script goes back to DRAFT, not its own stage")
    void changeRequestedFallsBackToDraft() {
        assertThat(project(ScriptStatus.CHANGE_REQUESTED, VideoStatus.NOT_RENDERED).getPipelineStatus())
            .isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    @DisplayName("a submitted script reads as SCRIPT_REVIEW")
    void inReview() {
        assertThat(project(ScriptStatus.IN_REVIEW, VideoStatus.NOT_RENDERED).getPipelineStatus())
            .isEqualTo(ProjectStatus.SCRIPT_REVIEW);
    }

    @Test
    @DisplayName("an approved script with no clip yet reads as APPROVED")
    void approvedBeforeRender() {
        assertThat(project(ScriptStatus.APPROVED, VideoStatus.NOT_RENDERED).getPipelineStatus())
            .isEqualTo(ProjectStatus.APPROVED);
    }

    @Test
    @DisplayName("a running render reads as IN_PRODUCTION")
    void rendering() {
        assertThat(project(ScriptStatus.APPROVED, VideoStatus.RENDERING).getPipelineStatus())
            .isEqualTo(ProjectStatus.IN_PRODUCTION);
    }

    @Test
    @DisplayName("a clip awaiting or holding QA sign-off reads as CLIP_REVIEW")
    void clipReview() {
        assertThat(project(ScriptStatus.APPROVED, VideoStatus.IN_QA).getPipelineStatus())
            .isEqualTo(ProjectStatus.CLIP_REVIEW);
        assertThat(project(ScriptStatus.APPROVED, VideoStatus.APPROVED).getPipelineStatus())
            .isEqualTo(ProjectStatus.CLIP_REVIEW);
    }

    @Test
    @DisplayName("a published clip reads as PUBLISHED")
    void published() {
        assertThat(project(ScriptStatus.APPROVED, VideoStatus.PUBLISHED).getPipelineStatus())
            .isEqualTo(ProjectStatus.PUBLISHED);
    }

    @Test
    @DisplayName("archiving overrides every other state")
    void archivedWins() {
        ClipProject archived = project(ScriptStatus.APPROVED, VideoStatus.PUBLISHED);
        archived.setArchived(true);

        assertThat(archived.getPipelineStatus()).isEqualTo(ProjectStatus.ARCHIVED);
    }
}
