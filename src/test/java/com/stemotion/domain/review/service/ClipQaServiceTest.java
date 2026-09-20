package com.stemotion.domain.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.review.dto.QaDtos.QaCheck;
import com.stemotion.domain.review.dto.QaDtos.QaReport;
import com.stemotion.domain.review.dto.QaDtos.QaSeverity;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import com.stemotion.domain.script.service.ScriptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pre-publish QA is the last automated gate before a clip reaches students,
 * so each check is pinned to the condition it is supposed to catch.
 */
class ClipQaServiceTest {

    private ScriptService scriptService;
    private ClipQaService qaService;
    private ClipProject project;

    @BeforeEach
    void setUp() {
        scriptService = mock(ScriptService.class);
        qaService = new ClipQaService(
            scriptService,
            new SceneMapper(new ObjectMapper(), new ScenePropsValidator())
        );

        project = ClipProject.builder()
            .title("Dinh ly Pythagore")
            .subject(StemSubject.Math)
            .fps(30)
            .videoUrl("http://minio/clip.mp4")
            .build();
        project.setId("project-1");

        when(scriptService.getOrThrow(anyString())).thenReturn(project);
        when(scriptService.currentVersion(any())).thenReturn(new ScriptVersion());
    }

    private Scene scene(SceneType type, String title, String narration, int frames, String props) {
        Scene s = Scene.builder()
            .sceneType(type)
            .title(title)
            .narration(narration)
            .durationInFrames(frames)
            .props(props)
            .build();
        s.setId("scene-" + type);
        return s;
    }

    /** Three well-paced scenes opening with a title and closing with an outro. */
    private List<Scene> healthyScenes() {
        return List.of(
            scene(SceneType.TITLE_HERO, "Mo dau", "Hom nay chung ta hoc dinh ly Pythagore", 300, "{}"),
            scene(SceneType.MATH_FORMULA, "Cong thuc", "Binh phuong canh huyen bang tong hai canh", 300, "{}"),
            scene(SceneType.OUTRO, "Tong ket", "Cac em hay lam bai tap ve nha", 300, "{}")
        );
    }

    private QaReport runWith(List<Scene> scenes) {
        when(scriptService.scenesOf(any())).thenReturn(scenes);
        return qaService.run("project-1");
    }

    @Test
    @DisplayName("a well-formed clip passes with no findings")
    void healthyClipPasses() {
        QaReport report = runWith(healthyScenes());

        assertThat(report.passed()).isTrue();
        assertThat(report.failCount()).isZero();
        assertThat(report.warnCount()).isZero();
    }

    @Test
    @DisplayName("narration that cannot be read in time fails")
    void catchesNarrationOverrun() {
        String longNarration = "tu ".repeat(60);
        QaReport report = runWith(List.of(
            scene(SceneType.TITLE_HERO, "Mo dau", longNarration, 45, "{}")
        ));

        assertThat(report.passed()).isFalse();
        assertThat(report.findings())
            .anyMatch(f -> f.check() == QaCheck.NARRATION_PACING && f.severity() == QaSeverity.FAIL);
    }

    @Test
    @DisplayName("a clip under the minimum length fails")
    void catchesTooShortClip() {
        QaReport report = runWith(List.of(
            scene(SceneType.TITLE_HERO, "Mo dau", "Ngan", 60, "{}")
        ));

        assertThat(report.findings())
            .anyMatch(f -> f.check() == QaCheck.DURATION_BOUNDS
                && f.severity() == QaSeverity.FAIL
                && f.message().contains("ngắn hơn"));
    }

    @Test
    @DisplayName("a scene too short to read fails")
    void catchesTooShortScene() {
        List<Scene> scenes = List.of(
            scene(SceneType.TITLE_HERO, "Mo dau", "Chao cac em", 20, "{}"),
            scene(SceneType.MATH_FORMULA, "Cong thuc", "Binh phuong canh huyen", 400, "{}"),
            scene(SceneType.OUTRO, "Tong ket", "Hen gap lai", 400, "{}")
        );

        assertThat(runWith(scenes).findings())
            .anyMatch(f -> f.check() == QaCheck.DURATION_BOUNDS
                && f.severity() == QaSeverity.FAIL
                && f.message().contains("quá ngắn"));
    }

    @Test
    @DisplayName("an over-long title is caught as overflow")
    void catchesTitleOverflow() {
        String longTitle = "T".repeat(90);
        List<Scene> scenes = List.of(
            scene(SceneType.TITLE_HERO, longTitle, "Chao cac em hoc sinh than men", 300, "{}"),
            scene(SceneType.OUTRO, "Tong ket", "Hen gap lai cac em", 300, "{}")
        );

        assertThat(runWith(scenes).findings())
            .anyMatch(f -> f.check() == QaCheck.ON_SCREEN_TEXT_OVERFLOW
                && f.severity() == QaSeverity.FAIL);
    }

    @Test
    @DisplayName("a pale accent colour is flagged for contrast")
    void catchesLowContrastColour() {
        List<Scene> scenes = List.of(
            scene(SceneType.TITLE_HERO, "Mo dau", "Chao cac em hoc sinh than men", 300,
                "{\"flaskColor\":\"#fdfdfd\"}"),
            scene(SceneType.OUTRO, "Tong ket", "Hen gap lai cac em", 300, "{}")
        );

        assertThat(runWith(scenes).findings())
            .anyMatch(f -> f.check() == QaCheck.CONTRAST_READABILITY
                && f.severity() == QaSeverity.WARN);
    }

    @Test
    @DisplayName("a dark accent colour passes the contrast check")
    void acceptsHighContrastColour() {
        List<Scene> scenes = List.of(
            scene(SceneType.TITLE_HERO, "Mo dau", "Chao cac em hoc sinh than men", 300,
                "{\"flaskColor\":\"#0b3d91\"}"),
            scene(SceneType.MATH_FORMULA, "Cong thuc", "Binh phuong canh huyen bang tong", 300, "{}"),
            scene(SceneType.OUTRO, "Tong ket", "Hen gap lai cac em", 300, "{}")
        );

        assertThat(runWith(scenes).findings())
            .noneMatch(f -> f.check() == QaCheck.CONTRAST_READABILITY);
    }

    @Test
    @DisplayName("a project with no rendered clip cannot pass")
    void requiresRenderedVideo() {
        project.setVideoUrl(null);

        QaReport report = runWith(healthyScenes());

        assertThat(report.passed()).isFalse();
        assertThat(report.findings())
            .anyMatch(f -> f.check() == QaCheck.RENDER_FRESHNESS && f.severity() == QaSeverity.FAIL);
    }

    @Test
    @DisplayName("a missing opening or closing scene is a warning, not a block")
    void structureIssuesOnlyWarn() {
        List<Scene> scenes = List.of(
            scene(SceneType.MATH_FORMULA, "Cong thuc", "Binh phuong canh huyen bang tong", 450, "{}"),
            scene(SceneType.STEM_QUIZ, "Cau hoi", "Cac em hay chon dap an dung nhat", 450, "{}")
        );

        QaReport report = runWith(scenes);

        assertThat(report.passed()).isTrue();
        assertThat(report.warnCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("an empty script fails outright")
    void emptyScriptFails() {
        QaReport report = runWith(List.of());

        assertThat(report.passed()).isFalse();
        assertThat(report.findings())
            .anyMatch(f -> f.check() == QaCheck.SCENE_STRUCTURE && f.severity() == QaSeverity.FAIL);
    }
}
