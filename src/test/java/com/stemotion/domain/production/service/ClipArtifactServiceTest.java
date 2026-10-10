package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.ClipRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClipArtifactServiceTest {
    private final ClipRepository repository = mock(ClipRepository.class);
    private final ClipArtifactService service = new ClipArtifactService(repository);

    private RenderJob completed() {
        RenderJob job = RenderJob.builder().status(RenderStatus.COMPLETED)
            .videoUrl("https://storage.example/video.mp4").build();
        job.setId("job-1");
        return job;
    }

    @Test void createsArtifactWithoutInventingQaOrDuration() {
        when(repository.findByRenderJobId("job-1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Clip clip = service.recordCompleted(completed());
        assertThat(clip.getQaStatus()).isEqualTo(Clip.QaStatus.PENDING);
        assertThat(clip.getDurationSec()).isNull();
        assertThat(clip.getQaReport()).isNull();
        assertThat(clip.getVideoUrl()).isEqualTo("https://storage.example/video.mp4");
    }

    @Test void replayPreservesOriginalArtifactAndQa() {
        Clip existing = Clip.builder().videoUrl(completed().getVideoUrl())
            .qaStatus(Clip.QaStatus.PASS).durationSec(42).build();
        when(repository.findByRenderJobId("job-1")).thenReturn(Optional.of(existing));
        assertThat(service.recordCompleted(completed())).isSameAs(existing);
        verify(repository, never()).save(any());
        assertThat(existing.getQaStatus()).isEqualTo(Clip.QaStatus.PASS);
    }

    @Test void rejectsRebindingExistingArtifact() {
        when(repository.findByRenderJobId("job-1")).thenReturn(Optional.of(
            Clip.builder().videoUrl("https://storage.example/original.mp4").build()));
        assertThatThrownBy(() -> service.recordCompleted(completed())).isInstanceOf(ApiException.class);
        verify(repository, never()).save(any());
    }

    @Test void incompleteAndMissingUrlCannotCreateArtifact() {
        RenderJob job = completed();
        job.setStatus(RenderStatus.PROCESSING);
        assertThatThrownBy(() -> service.recordCompleted(job)).isInstanceOf(ApiException.class);
        job.setStatus(RenderStatus.COMPLETED);
        job.setVideoUrl(" ");
        assertThatThrownBy(() -> service.recordCompleted(job)).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }
}
