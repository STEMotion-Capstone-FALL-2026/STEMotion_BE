package com.stemotion.domain.script.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest;
import com.stemotion.domain.script.entity.*;
import com.stemotion.domain.script.mapper.*;
import com.stemotion.domain.script.repository.*;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SceneIdentityTest {
    final SceneRepository scenes = mock(SceneRepository.class);
    final ClipProjectRepository projects = mock(ClipProjectRepository.class);
    final ScriptVersionRepository versions = mock(ScriptVersionRepository.class);
    final SceneMapper mapper = new SceneMapper(new ObjectMapper(), new ScenePropsValidator());
    final ScriptService service = new ScriptService(projects, versions, scenes, mapper, mock(AuthService.class),
        mock(WorkspaceService.class), mock(StatusHistoryService.class), mock(WorkspaceAccess.class));
    final ScriptVersion version = ScriptVersion.builder().versionNo(1).build();
    final String first = "11111111-1111-4111-8111-111111111111";
    final String second = "22222222-2222-4222-8222-222222222222";

    SceneRequest request(String id, String title) {
        var request = new SceneRequest(); request.setId(id); request.setTitle(title);
        request.setType(SceneType.TITLE_HERO); request.setDurationInFrames(90);
        Map.of("subtitle", "Test", "subject", "Math", "gradeLevel", "10", "badgeText", "STEM").forEach(request::putProp);
        return request;
    }
    List<Scene> fixture() {
        version.setId("version");
        var workspace = new Workspace(); workspace.setId("workspace");
        var owner = new User(); owner.setId("owner"); owner.setFullName("Owner");
        var project = ClipProject.builder().title("Test").subject(StemSubject.Math).fps(30).scriptStatus(ScriptStatus.DRAFT)
            .videoStatus(VideoStatus.NOT_RENDERED).workspace(workspace).owner(owner).build();
        project.setId("project");
        when(projects.findById("project")).thenReturn(Optional.of(project));
        when(versions.findFirstByProjectIdAndIsCurrentTrue("project")).thenReturn(Optional.of(version));
        var one = mapper.toEntity(request(null, "One"), 1, version); one.setId(first);
        var two = mapper.toEntity(request(null, "Two"), 2, version); two.setId(second);
        List<Scene> existing = List.of(one, two);
        when(scenes.findByScriptVersionIdOrderByOrderNoAsc("version")).thenReturn(existing);
        when(scenes.findForUpdateByScriptVersionId("version")).thenReturn(existing);
        when(scenes.saveAll(any())).thenAnswer(call -> {
            List<Scene> saved = call.getArgument(0);
            saved.forEach(scene -> { if (scene.getId() == null) scene.setId(UUID.randomUUID().toString()); });
            return saved;
        });
        return existing;
    }
    void save(SceneRequest... requests) {
        service.updateDraft("project", new UpdateScriptRequest(null, null, null, null, null, List.of(requests)));
    }
    @Test void editingAndReorderingKeepOriginalRowsAndFeedbackIdentity() {
        var existing = fixture();
        save(request(second, "Two edited"), request(first, "One edited"));
        assertThat(existing.get(0).getId()).isEqualTo(first);
        assertThat(existing.get(0).getOrderNo()).isEqualTo(2);
        assertThat(existing.get(1).getOrderNo()).isEqualTo(1);
        assertThat(existing.get(0).getTitle()).isEqualTo("One edited");
        verify(scenes, never()).deleteByScriptVersionId(any());
        verify(scenes).deleteAll(List.of());
    }
    @Test void addingSceneDoesNotRecreateExistingScenes() {
        fixture();
        var response = service.updateDraft("project", new UpdateScriptRequest(null, null, null, null, null,
            List.of(request(first, "One"), request(second, "Two"), request("scene_new", "Three"))));
        assertThat(response.scenes().get(0).getId()).isEqualTo(first);
        assertThat(response.scenes().get(1).getId()).isEqualTo(second);
        assertThat(response.scenes().get(2).getId()).isNotEqualTo("scene_new");
    }
    @Test void feedbackPreventsDeletionEvenIfFeedbackIsResolved() {
        fixture(); when(scenes.hasFeedback(second)).thenReturn(true);
        assertThatThrownBy(() -> save(request(first, "One"))).hasMessageContaining("feedback");
        verify(scenes, never()).deleteAll(any()); verify(scenes, never()).saveAll(any());
    }
    @Test void deletionWithoutFeedbackRemovesOnlyOmittedScene() {
        var existing = fixture(); save(request(first, "One"));
        verify(scenes).deleteAll(List.of(existing.get(1)));
    }
    @Test void foreignVersionIdAndDuplicatesAreRejectedBeforeWrites() {
        fixture();
        assertThatThrownBy(() -> save(request(UUID.randomUUID().toString(), "Foreign"))).hasMessageContaining("current script version");
        assertThatThrownBy(() -> save(request(first, "One"), request(first, "Duplicate"))).hasMessageContaining("Duplicate");
        verify(scenes, never()).deleteAll(any()); verify(scenes, never()).saveAll(any());
    }
    @Test void legacyPropIdIsRecoveredAndCannotShadowCanonicalId() throws Exception {
        var existing = fixture();
        existing.get(0).setProps("{\"id\":\"scene_legacy\",\"subtitle\":\"Test\"}");
        String wire = new ObjectMapper().writeValueAsString(mapper.toResponse(existing.get(0)));
        assertThat(new ObjectMapper().readTree(wire).path("id").asText()).isEqualTo(first);
        save(request("scene_legacy", "One"), request(second, "Two"));
        assertThat(existing.get(0).getId()).isEqualTo(first);
        assertThat(existing.get(0).getProps()).doesNotContain("scene_legacy");
    }
}
