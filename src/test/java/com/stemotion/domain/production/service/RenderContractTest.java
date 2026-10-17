package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.domain.production.dto.ProductionDtos.StartRenderRequest;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.entity.*;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.integration.client.RenderServiceClient;
import com.stemotion.integration.config.IntegrationProperties;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RenderContractTest {
    @Test void legacy4k60RequestProduces1080p30WithoutChangingTimelineAndUsesVoice() {
        var jobs=mock(RenderJobRepository.class);var scripts=mock(ScriptService.class);
        var compositions=mock(CompositionService.class);var worker=mock(RenderServiceClient.class);
        var project=ClipProject.builder().title("Force").subject(StemSubject.Physics).fps(60)
            .scriptStatus(ScriptStatus.APPROVED).videoStatus(VideoStatus.NOT_RENDERED).build();project.setId("project");
        var version=new ScriptVersion();
        when(scripts.getOrThrow("project")).thenReturn(project);when(scripts.currentVersion(project)).thenReturn(version);
        Map<String,Object> scene=new LinkedHashMap<>(Map.of("id","scene","type","TITLE_HERO","title","Force",
            "narration","Narration","durationInFrames",600));
        when(compositions.renderInput(version)).thenReturn(new CompositionService.RenderInput(null,null,List.of(scene)));
        when(jobs.save(any())).thenAnswer(i->{RenderJob job=i.getArgument(0);job.setId("job");return job;});
        when(worker.enqueue(eq("job"),anyString(),anyMap(),eq(1920),eq(1080),eq(30),eq("leminh")))
            .thenAnswer(i->{
                Map<?,?> payload=i.getArgument(2);assertThat(payload.get("fps")).isEqualTo(30);
                var output=(Map<?,?>)((List<?>)payload.get("scenes")).get(0);
                assertThat(output.get("durationInFrames")).isEqualTo(300); // Ten seconds remains ten seconds.
                return new RenderServiceClient.EnqueueResult("external",5);
            });
        var service=new RenderService(jobs,mock(ClipProjectRepository.class),scripts,compositions,new com.fasterxml.jackson.databind.ObjectMapper(),worker,mock(AuthService.class),new IntegrationProperties(),mock(ClipArtifactService.class));
        service.start("project",new StartRenderRequest("4k",60,"leminh"),"producer");
        var captured=org.mockito.ArgumentCaptor.forClass(RenderJob.class);verify(jobs,times(2)).save(captured.capture());
        assertThat(captured.getValue().getFps()).isEqualTo(30);
        assertThat(captured.getValue().getResolution()).isEqualTo(RenderResolution.RES_1080P);
        verify(worker).enqueue(eq("job"),anyString(),anyMap(),eq(1920),eq(1080),eq(30),eq("leminh"));
        assertThat(project.getFps()).isEqualTo(60); // Persisted script/editor timeline is untouched.
    }

    @Test void theWorkerHearsAboutAJobOnlyAfterItsRowIsCommitted() {
        var jobs=mock(RenderJobRepository.class);var scripts=mock(ScriptService.class);var projects=mock(ClipProjectRepository.class);
        var compositions=mock(CompositionService.class);var worker=mock(RenderServiceClient.class);
        var project=ClipProject.builder().title("Force").subject(StemSubject.Physics).fps(30)
            .scriptStatus(ScriptStatus.APPROVED).videoStatus(VideoStatus.NOT_RENDERED).build();project.setId("project");
        var version=new ScriptVersion();
        when(scripts.getOrThrow("project")).thenReturn(project);when(scripts.currentVersion(project)).thenReturn(version);
        when(compositions.renderInput(version)).thenReturn(new CompositionService.RenderInput(null,null,List.of(
            new LinkedHashMap<>(Map.of("id","scene","type","TITLE_HERO","title","Force","narration","N","durationInFrames",90)))));
        when(jobs.save(any())).thenAnswer(i->{RenderJob job=i.getArgument(0);job.setId("job");return job;});
        when(worker.enqueue(anyString(),anyString(),anyMap(),anyInt(),anyInt(),anyInt(),any()))
            .thenReturn(new RenderServiceClient.EnqueueResult("external",5))
            .thenThrow(new IllegalStateException("connection refused"));
        when(jobs.failQueued(eq("job"),anyString(),eq(RenderStatus.QUEUED),eq(RenderStatus.FAILED))).thenReturn(1);
        when(projects.findById("project")).thenReturn(Optional.of(project));
        var service=new RenderService(jobs,projects,scripts,compositions,new com.fasterxml.jackson.databind.ObjectMapper(),worker,mock(AuthService.class),new IntegrationProperties(),mock(ClipArtifactService.class));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.start("project",new StartRenderRequest(null,null,null),"producer");
            verifyNoInteractions(worker); // still inside the transaction
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
            verify(jobs).setExternalJobId("job","external");

            // A second render whose worker is unreachable fails the job and frees the project.
            project.setVideoStatus(VideoStatus.NOT_RENDERED);
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
            service.start("project",new StartRenderRequest(null,null,null),"producer");
            assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.RENDERING);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
            assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.NOT_RENDERED);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
