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
import com.stemotion.domain.production.entity.Clip;
import com.stemotion.domain.production.repository.ClipRepository;
import com.stemotion.domain.production.service.CompositionService;
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
    private CompositionService compositionService;
    private ClipRepository clipRepository;
    private ClipQaService qaService;
    private ClipProject project;

    @BeforeEach
    void setUp() {
        scriptService = mock(ScriptService.class);
        compositionService = mock(CompositionService.class);
        // No composition saved: QA checks the approved script's scenes.
        when(compositionService.renderScenes(any())).thenAnswer(i -> scriptService.scenesOf(i.getArgument(0)));
        clipRepository = mock(ClipRepository.class);
        qaService = new ClipQaService(
            scriptService,
            new SceneMapper(new ObjectMapper(), new ScenePropsValidator()),
            compositionService,
            clipRepository,
            new ObjectMapper()
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
    @DisplayName("QA checks the producer's composition, the clip as rendered, not the approved script")
    void checksTheRenderedComposition() {
        // The approved script is too short (6 s); the producer lengthened it to 30 s.
        when(scriptService.scenesOf(any())).thenReturn(List.of(
            scene(SceneType.TITLE_HERO, "Mo dau", "Hom nay", 60, "{}"),
            scene(SceneType.MATH_FORMULA, "Cong thuc", "a mu hai", 60, "{}"),
            scene(SceneType.OUTRO, "Tong ket", "Het", 60, "{}")));
        when(compositionService.renderScenes(any())).thenReturn(healthyScenes());

        assertThat(qaService.run("project-1").passed()).isTrue();
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

    private void renderedWithCaptions(String captionSyncJson) {
        Clip clip = Clip.builder().videoUrl("http://minio/clip.mp4")
            .qaReport(captionSyncJson == null ? null : "{\"captionSync\":" + captionSyncJson + "}").build();
        when(clipRepository.findCurrentArtifacts(any(), any(), any())).thenReturn(List.of(clip));
    }

    @Test
    @DisplayName("a clip rendered without Whisper captions is warned about, not blocked")
    void missingCaptionsOnlyWarn() {
        renderedWithCaptions(null);
        QaReport report = runWith(healthyScenes());
        assertThat(report.passed()).isTrue();
        assertThat(report.findings()).anyMatch(f -> f.check() == QaCheck.CAPTION_SYNC && f.severity() == QaSeverity.WARN);
    }

    @Test
    @DisplayName("captions running past their scene fail the caption/audio sync check")
    void captionsPastTheSceneFail() {
        renderedWithCaptions("{\"status\":\"ALIGNED\",\"scenes\":[{\"sceneId\":\"s1\",\"status\":\"ALIGNED\","
            + "\"narrationWords\":10,\"alignedWords\":10,\"sceneSec\":4.0,\"lastWordEnd\":4.6}]}");
        QaReport report = runWith(healthyScenes());
        assertThat(report.passed()).isFalse();
        assertThat(report.findings()).anyMatch(f -> f.check() == QaCheck.CAPTION_SYNC
            && f.severity() == QaSeverity.FAIL && "s1".equals(f.sceneId()));
    }

    @Test
    @DisplayName("aligned captions within their scenes pass; flagged words only warn")
    void alignedCaptionsPass() {
        renderedWithCaptions("{\"status\":\"ALIGNED\",\"scenes\":[{\"sceneId\":\"s1\",\"status\":\"ALIGNED\","
            + "\"narrationWords\":10,\"alignedWords\":10,\"sceneSec\":10.0,\"lastWordEnd\":7.2,\"lowConfidenceWords\":1}]}");
        QaReport report = runWith(healthyScenes());
        assertThat(report.passed()).isTrue();
        assertThat(report.failCount()).isZero();
        assertThat(report.findings()).anyMatch(f -> f.check() == QaCheck.CAPTION_SYNC && f.severity() == QaSeverity.WARN);
    }

    @Test
    @DisplayName("narration timed from its real TTS audio is judged by that timing, not the word-rate estimate")
    void measuredNarrationOverridesTheEstimate() {
        String narration = "tu ".repeat(20).trim(); // the estimate wants ~9.1 s
        String fits = "{\"captionWords\":[{\"text\":\"tu\",\"start\":0.1,\"end\":3.8}]}";
        String overruns = "{\"captionWords\":[{\"text\":\"tu\",\"start\":0.1,\"end\":5.2}]}";

        QaReport timed = runWith(List.of(scene(SceneType.TITLE_HERO, "Mo dau", narration, 126, fits)));
        assertThat(timed.findings()).noneMatch(f -> f.check() == QaCheck.NARRATION_PACING);

        QaReport late = runWith(List.of(scene(SceneType.TITLE_HERO, "Mo dau", narration, 126, overruns)));
        assertThat(late.findings())
            .anyMatch(f -> f.check() == QaCheck.NARRATION_PACING && f.severity() == QaSeverity.FAIL);
    }

    @Test
    @DisplayName("a scene the rendered clip aligned is paced by CAPTION_SYNC alone")
    void renderedAlignmentReplacesTheEstimate() {
        renderedWithCaptions("{\"status\":\"ALIGNED\",\"scenes\":[{\"sceneId\":\"scene-TITLE_HERO\",\"status\":\"ALIGNED\","
            + "\"sceneSec\":4.2,\"lastWordEnd\":3.9,\"narrationWords\":20,\"alignedWords\":20}]}");
        QaReport report = runWith(List.of(scene(SceneType.TITLE_HERO, "Mo dau", "tu ".repeat(20).trim(), 126, "{}")));
        assertThat(report.findings()).noneMatch(f -> f.check() == QaCheck.NARRATION_PACING);
        assertThat(report.findings()).noneMatch(f -> f.check() == QaCheck.CAPTION_SYNC && f.severity() == QaSeverity.FAIL);
    }
}
