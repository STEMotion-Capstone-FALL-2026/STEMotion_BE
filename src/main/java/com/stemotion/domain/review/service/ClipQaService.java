package com.stemotion.domain.review.service;

import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.review.dto.QaDtos.QaCheck;
import com.stemotion.domain.review.dto.QaDtos.QaFinding;
import com.stemotion.domain.review.dto.QaDtos.QaReport;
import com.stemotion.domain.review.dto.QaDtos.QaSeverity;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Automated pre-publish QA from the project proposal: duration bounds,
 * narration pacing, on-screen text overflow and contrast readability.
 *
 * <p>Findings are advisory by default; only the checks that would produce a
 * clip unusable in a classroom are raised to FAIL and block publishing.
 */
@Service
@RequiredArgsConstructor
public class ClipQaService {

    /** A clip shorter than this reads as an accident rather than a lesson. */
    private static final int MIN_CLIP_SECONDS = 20;
    /** The proposal targets 60-90s clips; well past that loses attention. */
    private static final int MAX_CLIP_SECONDS = 180;

    private static final int MIN_SCENE_FRAMES = 45;
    private static final int MAX_SCENE_FRAMES = 900;

    /** Vietnamese narration is read at roughly 2.2 words per second. */
    private static final double WORDS_PER_SECOND = 2.2;

    /** Beyond this a title wraps past the safe area of a 16:9 frame. */
    private static final int MAX_TITLE_CHARS = 70;
    private static final int MAX_ON_SCREEN_TEXT_CHARS = 220;

    /** WCAG AA for large text. */
    private static final double MIN_CONTRAST_RATIO = 3.0;

    private final ScriptService scriptService;
    private final SceneMapper sceneMapper;

    @Transactional(readOnly = true)
    public QaReport run(String projectId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        List<Scene> scenes = scriptService.scenesOf(scriptService.currentVersion(project));

        List<QaFinding> findings = new ArrayList<>();
        int fps = project.getFps() == null || project.getFps() <= 0 ? 30 : project.getFps();

        checkStructure(scenes, findings);
        checkClipDuration(scenes, fps, findings);

        for (Scene scene : scenes) {
            SceneResponse dto = sceneMapper.toResponse(scene);
            double seconds = (double) scene.getDurationInFrames() / fps;

            checkSceneDuration(scene, findings);
            checkNarrationPacing(scene, seconds, findings);
            checkTextOverflow(scene, dto, findings);
            checkContrast(scene, dto, findings);
        }

        checkRenderFreshness(project, findings);

        long fails = findings.stream().filter(f -> f.severity() == QaSeverity.FAIL).count();
        long warns = findings.stream().filter(f -> f.severity() == QaSeverity.WARN).count();

        if (findings.isEmpty()) {
            findings.add(new QaFinding(
                QaCheck.SCENE_STRUCTURE, QaSeverity.PASS, null,
                "Tất cả kiểm tra tự động đều đạt.", null
            ));
        }

        return new QaReport(projectId, fails == 0, (int) fails, (int) warns, findings);
    }

    // ------------------------------------------------------------------ checks

    private void checkStructure(List<Scene> scenes, List<QaFinding> findings) {
        if (scenes.isEmpty()) {
            findings.add(new QaFinding(
                QaCheck.SCENE_STRUCTURE, QaSeverity.FAIL, null,
                "Kịch bản không có phân cảnh nào.",
                "Thêm ít nhất một phân cảnh trước khi xuất bản."
            ));
            return;
        }

        if (scenes.get(0).getSceneType() != SceneType.TITLE_HERO) {
            findings.add(new QaFinding(
                QaCheck.SCENE_STRUCTURE, QaSeverity.WARN, scenes.get(0).getId(),
                "Clip không mở đầu bằng phân cảnh tiêu đề.",
                "Đặt một phân cảnh TITLE_HERO ở đầu để học sinh nắm được chủ đề."
            ));
        }

        Scene last = scenes.get(scenes.size() - 1);
        if (last.getSceneType() != SceneType.OUTRO) {
            findings.add(new QaFinding(
                QaCheck.SCENE_STRUCTURE, QaSeverity.WARN, last.getId(),
                "Clip không kết thúc bằng phân cảnh tổng kết.",
                "Thêm phân cảnh OUTRO để chốt lại nội dung và gợi ý bài tiếp theo."
            ));
        }
    }

    private void checkClipDuration(List<Scene> scenes, int fps, List<QaFinding> findings) {
        int totalFrames = scenes.stream()
            .mapToInt(s -> s.getDurationInFrames() == null ? 0 : s.getDurationInFrames())
            .sum();
        int totalSeconds = Math.round((float) totalFrames / fps);

        if (totalSeconds < MIN_CLIP_SECONDS) {
            findings.add(new QaFinding(
                QaCheck.DURATION_BOUNDS, QaSeverity.FAIL, null,
                "Clip chỉ dài %ds, ngắn hơn mức tối thiểu %ds.".formatted(totalSeconds, MIN_CLIP_SECONDS),
                "Kéo dài các phân cảnh hoặc bổ sung nội dung."
            ));
        } else if (totalSeconds > MAX_CLIP_SECONDS) {
            findings.add(new QaFinding(
                QaCheck.DURATION_BOUNDS, QaSeverity.WARN, null,
                "Clip dài %ds, vượt mức khuyến nghị %ds.".formatted(totalSeconds, MAX_CLIP_SECONDS),
                "Cân nhắc tách thành nhiều clip ngắn."
            ));
        }
    }

    private void checkSceneDuration(Scene scene, List<QaFinding> findings) {
        int frames = scene.getDurationInFrames() == null ? 0 : scene.getDurationInFrames();

        if (frames < MIN_SCENE_FRAMES) {
            findings.add(new QaFinding(
                QaCheck.DURATION_BOUNDS, QaSeverity.FAIL, scene.getId(),
                "Phân cảnh \"%s\" chỉ %d frame, quá ngắn để đọc kịp.".formatted(scene.getTitle(), frames),
                "Đặt tối thiểu %d frame (%.1fs).".formatted(MIN_SCENE_FRAMES, MIN_SCENE_FRAMES / 30.0)
            ));
        } else if (frames > MAX_SCENE_FRAMES) {
            findings.add(new QaFinding(
                QaCheck.DURATION_BOUNDS, QaSeverity.WARN, scene.getId(),
                "Phân cảnh \"%s\" kéo dài %d frame.".formatted(scene.getTitle(), frames),
                "Chia nhỏ phân cảnh để giữ nhịp bài giảng."
            ));
        }
    }

    /**
     * Stands in for the caption/audio sync check until TTS is wired: narration
     * that cannot be read inside the scene would desync any generated audio.
     */
    private void checkNarrationPacing(Scene scene, double seconds, List<QaFinding> findings) {
        String narration = scene.getNarration();
        if (narration == null || narration.isBlank()) {
            findings.add(new QaFinding(
                QaCheck.NARRATION_PACING, QaSeverity.WARN, scene.getId(),
                "Phân cảnh \"%s\" chưa có lời thoại.".formatted(scene.getTitle()),
                "Thêm lời thoại để giọng đọc AI có nội dung."
            ));
            return;
        }

        int words = narration.trim().split("\\s+").length;
        double neededSeconds = words / WORDS_PER_SECOND;

        if (neededSeconds > seconds) {
            findings.add(new QaFinding(
                QaCheck.NARRATION_PACING, QaSeverity.FAIL, scene.getId(),
                "Lời thoại phân cảnh \"%s\" cần ~%.1fs nhưng chỉ có %.1fs."
                    .formatted(scene.getTitle(), neededSeconds, seconds),
                "Rút gọn lời thoại hoặc tăng thời lượng lên ít nhất %d frame."
                    .formatted((int) Math.ceil(neededSeconds * 30))
            ));
        } else if (neededSeconds < seconds * 0.3) {
            findings.add(new QaFinding(
                QaCheck.NARRATION_PACING, QaSeverity.WARN, scene.getId(),
                "Phân cảnh \"%s\" có nhiều khoảng lặng.".formatted(scene.getTitle()),
                "Giảm thời lượng hoặc bổ sung lời thoại."
            ));
        }
    }

    private void checkTextOverflow(Scene scene, SceneResponse dto, List<QaFinding> findings) {
        if (scene.getTitle() != null && scene.getTitle().length() > MAX_TITLE_CHARS) {
            findings.add(new QaFinding(
                QaCheck.ON_SCREEN_TEXT_OVERFLOW, QaSeverity.FAIL, scene.getId(),
                "Tiêu đề phân cảnh dài %d ký tự, sẽ tràn khung hình."
                    .formatted(scene.getTitle().length()),
                "Rút xuống dưới %d ký tự.".formatted(MAX_TITLE_CHARS)
            ));
        }

        // Props that the templates paint directly onto the frame.
        for (String key : List.of("subtitle", "diagramTitle", "question", "equation",
                                  "conclusion", "processTitle", "theoremName", "explanation")) {
            Object value = dto.getFlattenedProps().get(key);
            if (value instanceof String text && text.length() > MAX_ON_SCREEN_TEXT_CHARS) {
                findings.add(new QaFinding(
                    QaCheck.ON_SCREEN_TEXT_OVERFLOW, QaSeverity.WARN, scene.getId(),
                    "Trường \"%s\" dài %d ký tự, có thể tràn khung."
                        .formatted(key, text.length()),
                    "Rút xuống dưới %d ký tự.".formatted(MAX_ON_SCREEN_TEXT_CHARS)
                ));
            }
        }
    }

    /**
     * Checks any colour prop against a white frame. Templates render text on a
     * light background, so a pale accent colour leaves the text unreadable on a
     * projector.
     */
    private void checkContrast(Scene scene, SceneResponse dto, List<QaFinding> findings) {
        for (Map.Entry<String, Object> entry : dto.getFlattenedProps().entrySet()) {
            if (!entry.getKey().toLowerCase().contains("color")) {
                continue;
            }
            if (!(entry.getValue() instanceof String hex) || !hex.matches("#[0-9a-fA-F]{6}")) {
                continue;
            }

            double ratio = contrastAgainstWhite(hex);
            if (ratio < MIN_CONTRAST_RATIO) {
                findings.add(new QaFinding(
                    QaCheck.CONTRAST_READABILITY, QaSeverity.WARN, scene.getId(),
                    "Màu %s (%s) có độ tương phản %.1f:1 trên nền sáng."
                        .formatted(entry.getKey(), hex, ratio),
                    "Dùng màu đậm hơn để đạt tối thiểu %.1f:1.".formatted(MIN_CONTRAST_RATIO)
                ));
            }
        }
    }

    private void checkRenderFreshness(ClipProject project, List<QaFinding> findings) {
        if (project.getVideoUrl() == null) {
            findings.add(new QaFinding(
                QaCheck.RENDER_FRESHNESS, QaSeverity.FAIL, null,
                "Dự án chưa có video đã kết xuất.",
                "Chạy render trước khi xuất bản."
            ));
        }
    }

    // ------------------------------------------------------------- colour math

    /** WCAG contrast ratio of a hex colour against white. */
    private double contrastAgainstWhite(String hex) {
        double r = channel(Integer.parseInt(hex.substring(1, 3), 16));
        double g = channel(Integer.parseInt(hex.substring(3, 5), 16));
        double b = channel(Integer.parseInt(hex.substring(5, 7), 16));
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return (1.0 + 0.05) / (luminance + 0.05);
    }

    /** sRGB channel to linear light, per the WCAG definition. */
    private double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
