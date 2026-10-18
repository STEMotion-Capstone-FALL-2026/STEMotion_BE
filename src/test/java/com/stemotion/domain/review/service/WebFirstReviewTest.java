package com.stemotion.domain.review.service;

import com.stemotion.core.enums.StemEnums.FeedbackStatus;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.Composition;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.service.ClipArtifactService;
import com.stemotion.domain.production.service.CompositionService;
import com.stemotion.domain.production.service.RenderService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.entity.StatusHistory;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.review.entity.FeedbackComment;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.service.ScriptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class WebFirstReviewTest {

    final FeedbackCommentRepository comments = mock(FeedbackCommentRepository.class);
    final ClipProjectRepository projects = mock(ClipProjectRepository.class);
    final ScriptService scripts = mock(ScriptService.class);
    final AuthService auth = mock(AuthService.class);
    final ClipArtifactService clips = mock(ClipArtifactService.class);
    final StatusHistoryService history = mock(StatusHistoryService.class);
    final CompositionService compositions = mock(CompositionService.class);
    final RenderService renders = mock(RenderService.class);
    final FeedbackService service = new FeedbackService(comments, mock(SceneRepository.class), projects, scripts,
        auth, clips, history, compositions, renders);

    ClipProject project;
    ScriptVersion version;
    Composition composition;
    User reviewer;

    @BeforeEach
    void setUp() {
        project = ClipProject.builder().title("Ohm").subject(StemSubject.Physics)
            .scriptStatus(ScriptStatus.APPROVED).videoStatus(VideoStatus.NOT_RENDERED).archived(false).build();
        project.setId("project");
        version = new ScriptVersion();
        version.setId("version");
        composition = Composition.builder().project(project).scriptVersion(version).revision(3).scenes("[]").build();
        composition.setId("composition");
        reviewer = User.builder().fullName("Reviewer").role(User.UserRole.REVIEWER).build();
        reviewer.setId("reviewer");
        when(scripts.getOrThrow("project")).thenReturn(project);
        when(scripts.currentVersion(project)).thenReturn(version);
        when(auth.findUserOrThrow(anyString())).thenReturn(reviewer);
        when(compositions.ensure(eq(project), eq(version), anyString())).thenReturn(composition);
        when(compositions.renderInput(version)).thenReturn(new CompositionService.RenderInput(composition, 3, List.of()));
        when(clips.findCurrent(project, "version")).thenReturn(Optional.empty());
    }

    private Clip clipOfRevision(Integer revision) {
        RenderJob job = RenderJob.builder().project(project).compositionRevision(revision).build();
        Clip clip = Clip.builder().renderJob(job).videoUrl("https://clip").build();
        clip.setId("clip-" + revision);
        return clip;
    }

    @Test
    @DisplayName("the producer sends the composition for review without an MP4; it is frozen as IN_QA")
    void submitWithoutRendering() {
        service.submitForVideoReview("project", "producer");
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.IN_QA);
        verify(compositions).ensure(project, version, "producer");
        verify(history).record(eq(project), any(), eq(reviewer), org.mockito.ArgumentMatchers.contains("revision 3"));

        assertThatThrownBy(() -> service.submitForVideoReview("project", "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("already in review");
    }

    @Test
    @DisplayName("approving in the web player approves the composition revision and renders its MP4")
    void approveTheCompositionThenRender() {
        project.setVideoStatus(VideoStatus.IN_QA);
        service.approveVideo("project", "reviewer", "Đạt");
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.APPROVED);
        verify(history).recordReview(eq(project), any(), eq(reviewer), eq(StatusHistory.TargetType.COMPOSITION),
            eq("composition"), eq(StatusHistory.Decision.APPROVED), eq("Đạt"), eq("compositionRevision=3"));
        verify(renders).renderApproved("project", "reviewer");
    }

    @Test
    @DisplayName("a rendered clip of the reviewed revision is what gets approved, and nothing re-renders")
    void approveTheMatchingClip() {
        project.setVideoStatus(VideoStatus.IN_QA);
        when(clips.findCurrent(project, "version")).thenReturn(Optional.of(clipOfRevision(3)));
        service.approveVideo("project", "reviewer", null);
        verify(history).recordReview(eq(project), any(), eq(reviewer), eq(StatusHistory.TargetType.CLIP),
            eq("clip-3"), eq(StatusHistory.Decision.APPROVED), isNull());
        verifyNoInteractions(renders);
    }

    @Test
    @DisplayName("a clip of an earlier revision is not the reviewed composition")
    void staleClipDoesNotCount() {
        project.setVideoStatus(VideoStatus.IN_QA);
        when(clips.findCurrent(project, "version")).thenReturn(Optional.of(clipOfRevision(2)));
        service.approveVideo("project", "reviewer", null);
        verify(history).recordReview(eq(project), any(), eq(reviewer), eq(StatusHistory.TargetType.COMPOSITION),
            eq("composition"), eq(StatusHistory.Decision.APPROVED), isNull(), eq("compositionRevision=3"));
        verify(renders).renderApproved("project", "reviewer");
    }

    @Test
    @DisplayName("only the current clip's open notes and unscoped ones block approval")
    void gateCountsTheCurrentClipOnly() {
        project.setVideoStatus(VideoStatus.IN_QA);
        when(clips.findCurrent(project, "version")).thenReturn(Optional.of(clipOfRevision(3)));
        when(comments.countByProjectIdAndStatusAndParentIsNullAndClipIdIsNull("project", FeedbackStatus.OPEN)).thenReturn(0L);
        when(comments.countByProjectIdAndStatusAndParentIsNullAndClipId("project", FeedbackStatus.OPEN, "clip-3")).thenReturn(2L);
        assertThatThrownBy(() -> service.approveVideo("project", "reviewer", null))
            .isInstanceOf(ApiException.class).hasMessageContaining("2 open review note");
        // Notes on another clip are never counted.
        verify(comments, never()).countByProjectIdAndStatus(anyString(), any());
    }

    @Test
    @DisplayName("sending back in the web player records the reason against the composition revision")
    void sendBackTheComposition() {
        project.setVideoStatus(VideoStatus.IN_QA);
        service.sendBackToProduction("project", "reviewer", "Sai đơn vị ở cảnh 2");
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.NOT_RENDERED);
        verify(history).recordReview(eq(project), any(), eq(reviewer), eq(StatusHistory.TargetType.COMPOSITION),
            eq("composition"), eq(StatusHistory.Decision.REJECTED), eq("Sai đơn vị ở cảnh 2"), eq("compositionRevision=3"));
    }

    @Test
    @DisplayName("a reply joins the thread of its note, and resolving records who and when")
    void repliesAndResolveAudit() {
        FeedbackComment note = FeedbackComment.builder().project(project).author(reviewer).timestampSec(4.0)
            .clipId("clip-3").content("Chữ quá nhỏ").status(FeedbackStatus.OPEN).build();
        note.setId("note");
        FeedbackComment earlierReply = FeedbackComment.builder().project(project).author(reviewer).parent(note)
            .content("Đã sửa").status(FeedbackStatus.OPEN).build();
        earlierReply.setId("reply-1");
        when(comments.findById("note")).thenReturn(Optional.of(note));
        when(comments.findById("reply-1")).thenReturn(Optional.of(earlierReply));
        when(comments.save(any())).thenAnswer(i -> i.getArgument(0));

        var reply = service.reply("reply-1", "  Cảm ơn ", "producer");
        assertThat(reply.parentId()).isEqualTo("note");
        assertThat(reply.clipId()).isEqualTo("clip-3");
        assertThat(reply.timestampSec()).isEqualTo(4.0);
        assertThat(reply.content()).isEqualTo("Cảm ơn");

        var resolved = service.resolve("note", "producer");
        assertThat(resolved.status()).isEqualTo(FeedbackStatus.RESOLVED);
        assertThat(resolved.resolvedByName()).isEqualTo("Reviewer");
        assertThat(resolved.resolvedAt()).isNotNull();

        var reopened = service.reopen("note");
        assertThat(reopened.resolvedByName()).isNull();

        assertThatThrownBy(() -> service.resolve("reply-1", "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("reply");
    }

    @Test
    @DisplayName("a clip pin is scoped to the render being reviewed")
    void newPinsAreScopedToTheCurrentClip() {
        when(clips.findCurrent(project, "version")).thenReturn(Optional.of(clipOfRevision(3)));
        when(comments.save(any())).thenAnswer(i -> i.getArgument(0));
        var created = service.create("project",
            new com.stemotion.domain.review.dto.FeedbackDtos.FeedbackRequest(12.5, null, "Âm thanh lệch", null), "reviewer");
        assertThat(created.clipId()).isEqualTo("clip-3");
    }
}
