package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.script.dto.ScriptDtos.ScriptResponse;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.integration.client.AiGateway;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoStoryboardServiceTest {
    private final ScriptService scripts = mock(ScriptService.class);
    private final AiGateway gateway = mock(AiGateway.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final VideoStoryboardService service = new VideoStoryboardService(scripts, gateway, mapper);

    private ScriptResponse source(int revision) {
        var script = mock(ScriptResponse.class);
        when(script.currentVersionNo()).thenReturn(2);
        when(script.draftRevision()).thenReturn(revision);
        when(script.title()).thenReturn("Ohm"); when(script.subject()).thenReturn(StemSubject.Physics);
        when(script.gradeLevel()).thenReturn("10");
        when(script.scenes()).thenReturn(List.of(new SceneResponse("s1", null, "Ohm", "Narration", 90, Map.of())));
        return script;
    }

    @Test void disabledDoesNotCallProvider() {
        assertThatThrownBy(() -> service.suggest("p", 2, 3)).isInstanceOf(ApiException.class);
        verifyNoInteractions(gateway, scripts);
    }
    @Test void staleInputIsRefusedBeforeProvider() {
        ReflectionTestUtils.setField(service, "enabled", true);
        var loaded = source(4);
        when(scripts.findById("p")).thenReturn(loaded);
        assertThatThrownBy(() -> service.suggest("p", 2, 3)).isInstanceOf(ApiException.class);
        verifyNoInteractions(gateway);
    }
    @Test void returnsSuggestionBoundToSourceAndFlagsNewerEditsWithoutSaving() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", true);
        var original = source(3);
        var latest = source(4);
        when(scripts.findById("p")).thenReturn(original, latest);
        when(gateway.generateJson(any(), anyString(), anyString(), anyMap(), eq("storyboard"))).thenReturn(new AiGateway.Generation(
            mapper.readTree("""
                {"scenes":[{"sceneId":"s1","learningObjective":"Ohm","layout":"split",
                "visualDescription":"Circuit","motionCue":"Reveal label","pronunciationNotes":"",
                "reviewWarnings":[]}]}
                """), "actual-model", "request-1", 10, 5, 15));
        var result = service.suggest("p", 2, 3);
        assertThat(result.sourceChanged()).isTrue();
        assertThat(result.suggestion().requestId()).isEqualTo("request-1");
        verify(scripts, times(2)).findById("p"); verifyNoMoreInteractions(scripts);
    }
    @Test void changedSceneIdentityIsRejected() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", true);
        var loaded = source(3);
        when(scripts.findById("p")).thenReturn(loaded);
        when(gateway.generateJson(any(), anyString(), anyString(), anyMap(), eq("storyboard"))).thenReturn(new AiGateway.Generation(
            mapper.readTree("{\"scenes\":[{\"sceneId\":\"invented\"}]}"), "model", null, null, null, null));
        assertThatThrownBy(() -> service.suggest("p", 2, 3)).isInstanceOf(ApiException.class);
    }
}
