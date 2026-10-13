package com.stemotion.domain.script.service;

import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectArchiveTest {
    private final ClipProjectRepository projects=mock(ClipProjectRepository.class);
    private final AuthService auth=mock(AuthService.class);
    private final StatusHistoryService history=mock(StatusHistoryService.class);
    private final ScriptService service=new ScriptService(projects,null,null,null,auth,null,history,null);
    private ClipProject project(VideoStatus status) {
        var project=ClipProject.builder().scriptStatus(ScriptStatus.DRAFT).videoStatus(status).build();
        project.setId("project");when(projects.findById("project")).thenReturn(Optional.of(project));return project;
    }
    @Test void deleteArchivesAndRetainsRowsAndAudit() {
        var project=project(VideoStatus.NOT_RENDERED);
        service.delete("project","actor");
        assertThat(project.getArchived()).isTrue();
        verify(projects).save(project);verify(projects,never()).deleteById(any());
        verify(history).record(eq(project),any(),any(),eq("Project archived"));
        service.delete("project","actor");verify(projects,times(1)).save(project);
        assertThatThrownBy(()->service.requireActive(project)).isInstanceOf(ApiException.class);
    }
    @Test void runningRenderMustBeCancelledFirst() {
        var project=project(VideoStatus.RENDERING);
        assertThatThrownBy(()->service.delete("project","actor")).isInstanceOf(ApiException.class);
        assertThat(project.getArchived()).isFalse();verify(projects,never()).save(any());
    }
}
