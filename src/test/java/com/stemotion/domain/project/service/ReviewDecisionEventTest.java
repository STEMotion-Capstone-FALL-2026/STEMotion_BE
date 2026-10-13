package com.stemotion.domain.project.service;

import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.project.entity.*;
import com.stemotion.domain.project.repository.StatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewDecisionEventTest {
    @Test void unchangedAggregateStillRecordsArtifactDecision() {
        var repository=mock(StatusHistoryRepository.class);
        var project=ClipProject.builder().scriptStatus(ScriptStatus.APPROVED)
            .videoStatus(VideoStatus.APPROVED).build();project.setId("project");
        new StatusHistoryService(repository).recordReview(project,ProjectStatus.CLIP_REVIEW,
            new User(),StatusHistory.TargetType.CLIP,"clip",StatusHistory.Decision.APPROVED,"Ready");
        var captured=ArgumentCaptor.forClass(StatusHistory.class);
        verify(repository).save(captured.capture());
        var event=captured.getValue();
        assertThat(event.getFromStatus()).isEqualTo(event.getToStatus());
        assertThat(event.getEventType()).isEqualTo(StatusHistory.EventType.REVIEW_DECISION);
        assertThat(event.getTargetId()).isEqualTo("clip");
        assertThat(event.getDecision()).isEqualTo(StatusHistory.Decision.APPROVED);
        assertThat(event.getReason()).isEqualTo("Ready");
        assertThat(event.getNote()).isNull();
    }
}
