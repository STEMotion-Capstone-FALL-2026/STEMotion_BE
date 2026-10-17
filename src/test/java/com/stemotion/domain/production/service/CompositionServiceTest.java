package com.stemotion.domain.production.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.CompositionResponse;
import com.stemotion.domain.production.dto.ProductionDtos.UpdateCompositionRequest;
import com.stemotion.domain.production.entity.Composition;
import com.stemotion.domain.production.repository.CompositionRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import com.stemotion.domain.script.service.ScriptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Production Workspace (proposal): the producer opens the approved script as a
 * draft and edits per-scene properties; scenes, order and templates stay as
 * reviewed, and the approved script itself is never written.
 */
class CompositionServiceTest {

    private final CompositionRepository compositions = mock(CompositionRepository.class);
    private final ScriptService scripts = mock(ScriptService.class);
    private final AuthService auth = mock(AuthService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final CompositionService service = new CompositionService(compositions, scripts,
        new SceneMapper(json, new ScenePropsValidator()), auth, json);

    private ClipProject project;
    private ScriptVersion version;

    private static Scene scene(String id, SceneType type, String props) {
        Scene scene = Scene.builder().orderNo(1).sceneType(type).title("Câu hỏi").narration("Đọc to")
            .durationInFrames(150).props(props).build();
        scene.setId(id);
        return scene;
    }

    private static SceneRequest edit(String id, SceneType type, int frames, Map<String, Object> props) {
        SceneRequest request = new SceneRequest();
        request.setId(id);
        request.setType(type);
        request.setTitle("Câu hỏi mới");
        request.setNarration("Lời đọc đã chỉnh");
        request.setDurationInFrames(frames);
        props.forEach(request::putProp);
        return request;
    }

    private static Map<String, Object> quiz(int correctIndex) {
        return Map.of("question", "U = ?", "options", List.of("IR", "I/R"), "correctIndex", correctIndex,
            "hint", "Ôm", "explanation", "U = I·R");
    }

    @BeforeEach
    void setUp() {
        project = ClipProject.builder().title("Định luật Ôm").subject(StemSubject.Physics).fps(30)
            .scriptStatus(ScriptStatus.APPROVED).videoStatus(VideoStatus.NOT_RENDERED).build();
        project.setId("project");
        version = new ScriptVersion();
        version.setId("version");
        version.setVersionNo(3);
        when(scripts.getOrThrow("project")).thenReturn(project);
        when(scripts.currentVersion(project)).thenReturn(version);
        when(scripts.scenesOf(version)).thenReturn(List.of(scene("s1", SceneType.STEM_QUIZ,
            "{\"question\":\"U = ?\",\"options\":[\"IR\",\"I/R\"],\"correctIndex\":0,\"hint\":\"Ôm\",\"explanation\":\"U = I·R\"}")));
        when(compositions.findByScriptVersionId("version")).thenReturn(Optional.empty());
        when(compositions.lockByScriptVersionId("version")).thenReturn(Optional.empty());
        when(compositions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(compositions.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(auth.findUserOrThrow("producer")).thenReturn(User.builder().fullName("Producer").build());
    }

    @Test
    @DisplayName("opening an approved script gives an auto-generated draft from its scenes")
    void opensTheApprovedScriptAsDraft() {
        CompositionResponse draft = service.get("project");
        assertThat(draft.revision()).isZero();
        assertThat(draft.editable()).isTrue();
        assertThat(draft.scriptVersionNo()).isEqualTo(3);
        assertThat(draft.scenes()).hasSize(1);
        assertThat(draft.scenes().get(0)).containsEntry("id", "s1").containsEntry("type", "STEM_QUIZ")
            .containsEntry("question", "U = ?");
    }

    @Test
    @DisplayName("a script that is not approved has no production draft")
    void onlyApprovedScripts() {
        project.setScriptStatus(ScriptStatus.IN_REVIEW);
        assertThatThrownBy(() -> service.get("project")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("the producer's per-scene edits become composition revision 1; the script is untouched")
    void savesEditsAsNextRevision() {
        CompositionResponse saved = service.update("project",
            new UpdateCompositionRequest(0, List.of(edit("s1", SceneType.STEM_QUIZ, 240, quiz(1)))), "producer");

        assertThat(saved.revision()).isEqualTo(1);
        assertThat(saved.scenes().get(0)).containsEntry("title", "Câu hỏi mới")
            .containsEntry("narration", "Lời đọc đã chỉnh").containsEntry("durationInFrames", 240)
            .containsEntry("correctIndex", 1);
        ArgumentCaptor<Composition> stored = ArgumentCaptor.forClass(Composition.class);
        verify(compositions).saveAndFlush(stored.capture());
        assertThat(stored.getValue().getScriptVersion()).isSameAs(version);
        verify(scripts, never()).updateDraft(any(), any());
    }

    @Test
    @DisplayName("edits go through the same props validation as the script editor")
    void validatesProps() {
        assertThatThrownBy(() -> service.update("project",
            new UpdateCompositionRequest(0, List.of(edit("s1", SceneType.STEM_QUIZ, 240, quiz(5)))), "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("correctIndex");
    }

    @Test
    @DisplayName("scenes, their order and their templates stay as the reviewer approved them")
    void keepsTheApprovedStructure() {
        assertThatThrownBy(() -> service.update("project", new UpdateCompositionRequest(0,
            List.of(edit("other", SceneType.STEM_QUIZ, 240, quiz(0)))), "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("approved order");
        assertThatThrownBy(() -> service.update("project", new UpdateCompositionRequest(0,
            List.of(edit("s1", SceneType.OUTRO, 240, Map.of("summaryPoints", List.of("a"),
                "nextLessonSuggestion", "b", "instructorName", "c")))), "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("template");
        assertThatThrownBy(() -> service.update("project", new UpdateCompositionRequest(0,
            List.of(edit("s1", SceneType.STEM_QUIZ, 240, quiz(0)), edit("s2", SceneType.STEM_QUIZ, 240, quiz(0)))),
            "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("keep the 1 scenes");
        assertThatThrownBy(() -> service.update("project", new UpdateCompositionRequest(0,
            List.of(edit("s1", SceneType.STEM_QUIZ, 0, quiz(0)))), "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("duration");
    }

    @Test
    @DisplayName("two first saves at once: the one that loses the insert gets a 409, not a 500")
    void concurrentFirstSaveIsAConflict() {
        when(compositions.saveAndFlush(any())).thenThrow(
            new org.springframework.dao.DataIntegrityViolationException("compositions_script_version_id_key"));
        assertThatThrownBy(() -> service.update("project",
            new UpdateCompositionRequest(0, List.of(edit("s1", SceneType.STEM_QUIZ, 240, quiz(0)))), "producer"))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(409));
    }

    @Test
    @DisplayName("a save based on an older revision is refused instead of overwriting someone else's edit")
    void refusesStaleRevision() {
        Composition current = Composition.builder().project(project).scriptVersion(version).revision(2)
            .scenes("[]").build();
        when(compositions.lockByScriptVersionId("version")).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service.update("project",
            new UpdateCompositionRequest(1, List.of(edit("s1", SceneType.STEM_QUIZ, 240, quiz(0)))), "producer"))
            .isInstanceOf(ApiException.class).hasMessageContaining("revision 2");
    }

    @Test
    @DisplayName("while the clip renders, is in review or is approved, its composition cannot change")
    void lockedOutsideProduction() {
        for (VideoStatus status : List.of(VideoStatus.RENDERING, VideoStatus.IN_QA, VideoStatus.APPROVED,
                VideoStatus.PUBLISHED)) {
            project.setVideoStatus(status);
            assertThat(service.get("project").editable()).isFalse();
            assertThatThrownBy(() -> service.update("project",
                new UpdateCompositionRequest(0, List.of(edit("s1", SceneType.STEM_QUIZ, 240, quiz(0)))), "producer"))
                .isInstanceOf(ApiException.class);
        }
        // The reviewer sent the clip back to production: editable again.
        project.setVideoStatus(VideoStatus.NOT_RENDERED);
        assertThat(service.get("project").editable()).isTrue();
    }

    @Test
    @DisplayName("QA sees the composition's scenes, with the approved scene ids")
    void renderScenesForQa() throws Exception {
        Composition current = Composition.builder().project(project).scriptVersion(version).revision(3)
            .scenes(json.writeValueAsString(List.of(Map.of("id", "s1", "type", "STEM_QUIZ", "title", "Edited",
                "narration", "Đọc", "durationInFrames", 240, "question", "U = ?")))).build();
        when(compositions.findByScriptVersionId("version")).thenReturn(Optional.of(current));
        List<Scene> scenes = service.renderScenes(version);
        assertThat(scenes).hasSize(1);
        assertThat(scenes.get(0).getId()).isEqualTo("s1");
        assertThat(scenes.get(0).getDurationInFrames()).isEqualTo(240);
        assertThat(scenes.get(0).getSceneType()).isEqualTo(SceneType.STEM_QUIZ);
        assertThat(scenes.get(0).getProps()).contains("\"question\":\"U = ?\"").doesNotContain("durationInFrames");
    }

    @Test
    @DisplayName("a render takes the saved composition and its revision")
    void renderUsesTheComposition() throws Exception {
        Composition current = Composition.builder().project(project).scriptVersion(version).revision(2)
            .scenes(json.writeValueAsString(List.of(Map.of("id", "s1", "type", "STEM_QUIZ", "title", "Edited",
                "durationInFrames", 240)))).build();
        when(compositions.findByScriptVersionId("version")).thenReturn(Optional.of(current));
        CompositionService.RenderInput input = service.renderInput(version);
        assertThat(input.revision()).isEqualTo(2);
        assertThat(input.scenes().get(0)).containsEntry("title", "Edited");
    }
}
