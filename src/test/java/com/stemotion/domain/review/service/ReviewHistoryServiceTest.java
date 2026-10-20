package com.stemotion.domain.review.service;

import com.stemotion.domain.project.entity.ProjectStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.Composition;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.ClipRepository;
import com.stemotion.domain.production.repository.CompositionRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.entity.StatusHistory;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewHistoryServiceTest {

    @Test
    void decisionsOnScriptsClipsAndCompositionsReadBackWithReasonAndVersion() {
        var histories = mock(StatusHistoryRepository.class);
        var scripts = mock(ScriptService.class);
        var versions = mock(ScriptVersionRepository.class);
        var clips = mock(ClipRepository.class);
        var compositions = mock(CompositionRepository.class);
        var members = mock(WorkspaceMemberRepository.class);
        var service = new ReviewHistoryService(histories, scripts, versions, clips, compositions, members);

        Workspace workspace = Workspace.builder().name("Tổ Lý").build();
        workspace.setId("ws");
        ClipProject project = ClipProject.builder().workspace(workspace).build();
        project.setId("project");
        when(scripts.getOrThrow("project")).thenReturn(project);

        User reviewer = User.builder().fullName("Cô Lan").role(User.UserRole.WRITER).build();
        reviewer.setId("reviewer");
        when(members.findWithUserByWorkspaceId("ws"))
            .thenReturn(List.of(WorkspaceMember.builder().user(reviewer).role(User.UserRole.REVIEWER).build()));

        ScriptVersion v1 = new ScriptVersion(); v1.setId("v1"); v1.setVersionNo(1);
        ScriptVersion v2 = new ScriptVersion(); v2.setId("v2"); v2.setVersionNo(2);
        when(versions.findAllById(org.mockito.ArgumentMatchers.anyIterable())).thenReturn(List.of(v1));
        Clip clip = Clip.builder().renderJob(RenderJob.builder().scriptVersion(v2).compositionRevision(2).build()).build();
        clip.setId("clip");
        when(clips.findWithRenderJobByIdIn(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(clip));
        Composition composition = Composition.builder().scriptVersion(v2).revision(4).build();
        composition.setId("composition");
        when(compositions.findWithVersionByIdIn(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(composition));

        StatusHistory scriptReject = decision(reviewer, StatusHistory.TargetType.SCRIPT, "v1", StatusHistory.Decision.REJECTED, "Thiếu ví dụ", null);
        StatusHistory clipReject = decision(reviewer, StatusHistory.TargetType.CLIP, "clip", StatusHistory.Decision.REJECTED, "Âm lệch", null);
        StatusHistory compositionApprove = decision(reviewer, StatusHistory.TargetType.COMPOSITION, "composition",
            StatusHistory.Decision.APPROVED, null, "compositionRevision=3");
        StatusHistory transition = StatusHistory.builder().eventType(StatusHistory.EventType.PIPELINE_TRANSITION)
            .targetType(StatusHistory.TargetType.PROJECT).targetId("project").changedBy(reviewer)
            .toStatus(ProjectStatus.DRAFT).build();
        when(histories.findEventsWithActor("project", StatusHistory.EventType.REVIEW_DECISION))
            .thenReturn(List.of(compositionApprove, clipReject, scriptReject));

        assertThat(service.findByProject("project"))
            .extracting(e -> e.action(), e -> e.targetType(), e -> e.scriptVersionNo(), e -> e.clipId(),
                e -> e.compositionRevision(), e -> e.note(), e -> e.actorRole(), e -> e.actorName())
            .containsExactly(
                tuple("APPROVE", "COMPOSITION", 2, null, 3, null, "REVIEWER", "Cô Lan"),
                tuple("REJECT", "CLIP", 2, "clip", 2, "Âm lệch", "REVIEWER", "Cô Lan"),
                tuple("REJECT", "SCRIPT", 1, null, null, "Thiếu ví dụ", "REVIEWER", "Cô Lan"));
    }

    private static StatusHistory decision(User actor, StatusHistory.TargetType target, String targetId,
                                          StatusHistory.Decision decision, String reason, String note) {
        return StatusHistory.builder().eventType(StatusHistory.EventType.REVIEW_DECISION).targetType(target)
            .targetId(targetId).decision(decision).reason(reason).note(note).changedBy(actor)
            .toStatus(ProjectStatus.DRAFT).build();
    }
}
