package com.stemotion.domain.production.service;

import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.PublishRequest;
import com.stemotion.domain.production.entity.*;
import com.stemotion.domain.production.repository.*;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.review.dto.QaDtos.QaReport;
import com.stemotion.domain.review.service.ClipQaService;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.service.ScriptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicationFlowTest {
    private final PublishRecordRepository records=mock(PublishRecordRepository.class);
    private final PublicationRepository publications=mock(PublicationRepository.class);
    private final ClipProjectRepository projects=mock(ClipProjectRepository.class);
    private final ScriptService scripts=mock(ScriptService.class);
    private final AuthService auth=mock(AuthService.class);
    private final ClipQaService qa=mock(ClipQaService.class);
    private final ClipArtifactService artifacts=mock(ClipArtifactService.class);
    private final PublishService service=new PublishService(records,projects,scripts,auth,qa,publications,artifacts,new ObjectMapper());
    private ClipProject project;

    @BeforeEach void setup() {
        project=ClipProject.builder().videoStatus(VideoStatus.APPROVED)
            .videoUrl("https://storage.example/video.mp4").build();
        project.setId("project");
        var version=new ScriptVersion();version.setId("version");
        when(scripts.getOrThrow("project")).thenReturn(project);
        when(scripts.currentVersion(project)).thenReturn(version);
        var clip=Clip.builder().videoUrl(project.getVideoUrl()).build();clip.setId("clip");
        when(artifacts.requireCurrent(project,"version")).thenReturn(clip);
        when(auth.findUserOrThrow("user")).thenReturn(new User());
        when(qa.run("project")).thenReturn(new QaReport("project",true,0,0,List.of()));
        when(records.save(any())).thenAnswer(inv->{PublishRecord r=inv.getArgument(0);r.setId("legacy");return r;});
    }
    @Test void internalLibraryCreatesCanonicalAndLegacyRecords() {
        var result=service.publish("project",new PublishRequest(List.of(PublishTarget.INTERNAL_LIBRARY),"Video",null),"user");
        var captured=ArgumentCaptor.forClass(Publication.class);
        verify(publications).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getClip().getId()).isEqualTo("clip");
        assertThat(captured.getValue().getPlatform()).isEqualTo(Publication.Platform.INTERNAL_LIBRARY);
        assertThat(captured.getValue().getMetadata()).contains("legacy");
        assertThat(result.id()).isEqualTo("legacy");
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.PUBLISHED);
    }
    @Test void lmsUsesActualArtifactUrl() {
        var result=service.publish("project",new PublishRequest(List.of(PublishTarget.LMS),"Video",null),"user");
        assertThat(result.lmsEmbedCode()).contains("src=\"https://storage.example/video.mp4\"");
        verify(publications).saveAndFlush(any());
    }
    @Test void duplicatePlatformCannotProduceAnotherLegacyRecord() {
        when(publications.existsByProjectIdAndPlatform("project",Publication.Platform.INTERNAL_LIBRARY)).thenReturn(true);
        assertThatThrownBy(()->service.publish("project",new PublishRequest(List.of(PublishTarget.INTERNAL_LIBRARY),"Video",null),"user"))
            .isInstanceOf(ApiException.class);
        verify(records,never()).save(any());
    }
    @Test void failedQaCannotPublish() {
        when(qa.run("project")).thenReturn(new QaReport("project",false,1,0,List.of()));
        assertThatThrownBy(()->service.publish("project",new PublishRequest(List.of(PublishTarget.INTERNAL_LIBRARY),"Video",null),"user"))
            .isInstanceOf(ApiException.class);
        verify(records,never()).save(any());verify(publications,never()).saveAndFlush(any());
        assertThat(project.getVideoStatus()).isEqualTo(VideoStatus.APPROVED);
    }
    @Test void embedRejectsExecutableAndInsecureUrls() {
        assertThatThrownBy(()->VideoEmbed.build("javascript:alert(1)")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->VideoEmbed.build("http://storage.example/video.mp4")).isInstanceOf(ApiException.class);
        assertThat(VideoEmbed.build("https://storage.example/video.mp4?x=1&y=2")).contains("&amp;");
    }
}
