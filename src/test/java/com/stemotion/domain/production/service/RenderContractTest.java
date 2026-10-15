package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.domain.production.dto.ProductionDtos.StartRenderRequest;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.entity.*;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import com.stemotion.domain.script.mapper.SceneMapper;
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
        var mapper=mock(SceneMapper.class);var worker=mock(RenderServiceClient.class);
        var project=ClipProject.builder().title("Force").subject(StemSubject.Physics).fps(60)
            .scriptStatus(ScriptStatus.APPROVED).videoStatus(VideoStatus.NOT_RENDERED).build();project.setId("project");
        var version=new ScriptVersion();var scene=new Scene();
        when(scripts.getOrThrow("project")).thenReturn(project);when(scripts.currentVersion(project)).thenReturn(version);
        when(scripts.scenesOf(version)).thenReturn(List.of(scene));
        when(mapper.toResponse(scene)).thenReturn(new SceneResponse("scene",SceneType.TITLE_HERO,"Force","Narration",600,Map.of()));
        when(jobs.save(any())).thenAnswer(i->{RenderJob job=i.getArgument(0);job.setId("job");return job;});
        when(worker.enqueue(eq("job"),anyString(),anyMap(),eq(1920),eq(1080),eq(30),eq("leminh")))
            .thenAnswer(i->{
                Map<?,?> payload=i.getArgument(2);assertThat(payload.get("fps")).isEqualTo(30);
                var output=(Map<?,?>)((List<?>)payload.get("scenes")).get(0);
                assertThat(output.get("durationInFrames")).isEqualTo(300); // Ten seconds remains ten seconds.
                return new RenderServiceClient.EnqueueResult("external",5);
            });
        var service=new RenderService(jobs,mock(ClipProjectRepository.class),scripts,mapper,worker,mock(AuthService.class),new IntegrationProperties(),mock(ClipArtifactService.class));
        service.start("project",new StartRenderRequest("4k",60,"leminh"),"producer");
        var captured=org.mockito.ArgumentCaptor.forClass(RenderJob.class);verify(jobs,times(2)).save(captured.capture());
        assertThat(captured.getValue().getFps()).isEqualTo(30);
        assertThat(captured.getValue().getResolution()).isEqualTo(RenderResolution.RES_1080P);
        verify(worker).enqueue(eq("job"),anyString(),anyMap(),eq(1920),eq(1080),eq(30),eq("leminh"));
        assertThat(project.getFps()).isEqualTo(60); // Persisted script/editor timeline is untouched.
    }
}
