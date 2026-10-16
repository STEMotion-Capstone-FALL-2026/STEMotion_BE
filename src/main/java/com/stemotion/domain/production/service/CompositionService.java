package com.stemotion.domain.production.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.CompositionResponse;
import com.stemotion.domain.production.dto.ProductionDtos.UpdateCompositionRequest;
import com.stemotion.domain.production.entity.Composition;
import com.stemotion.domain.production.repository.CompositionRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.SceneDtos.SceneResponse;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production Workspace (proposal): the producer opens an approved script as
 * an auto-generated draft, an ordered scene list mapped to Remotion
 * templates, and edits each scene's properties before rendering.
 *
 * <p>The scenes, their order and their templates come from the approved
 * script and stay as reviewed. The producer tunes what the proposal lists:
 * text, narration, colours, assets, duration and transitions, i.e. the
 * title, narration, duration and template props of each scene. Props are
 * checked by the same validator as the script editor.
 */
@Service
@RequiredArgsConstructor
public class CompositionService {

    /** Ten minutes at 30 fps; a STEM clip is 60-90 s. */
    static final int MAX_SCENE_FRAMES = 18_000;

    private static final TypeReference<List<Map<String, Object>>> SCENES_TYPE = new TypeReference<>() {};

    private final CompositionRepository compositions;
    private final ScriptService scriptService;
    private final SceneMapper sceneMapper;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    /** What a render draws: the saved composition, or the approved script as it is. */
    public record RenderInput(Composition composition, Integer revision, List<Map<String, Object>> scenes) {}

    @Transactional(readOnly = true)
    public CompositionResponse get(String projectId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        requireApproved(project);
        ScriptVersion version = scriptService.currentVersion(project);
        Composition saved = compositions.findByScriptVersionId(version.getId()).orElse(null);
        List<Map<String, Object>> scenes = saved == null ? scriptScenes(version) : read(saved.getScenes());
        return response(project, version, saved, scenes);
    }

    @Transactional
    public CompositionResponse update(String projectId, UpdateCompositionRequest request, String userId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        scriptService.requireActive(project);
        requireApproved(project);
        String locked = lockedReason(project);
        if (locked != null) {
            throw ApiException.conflict(locked);
        }

        ScriptVersion version = scriptService.currentVersion(project);
        Composition current = compositions.lockByScriptVersionId(version.getId()).orElse(null);
        int currentRevision = current == null ? 0 : current.getRevision();
        if (request.baseRevision() != currentRevision) {
            throw ApiException.conflict("The composition is now at revision " + currentRevision
                + " (edited elsewhere); reload it before saving");
        }

        List<Map<String, Object>> scenes = normalize(scriptService.scenesOf(version), request.scenes(), version);
        Composition composition = current != null ? current
            : Composition.builder().project(project).scriptVersion(version).build();
        composition.setRevision(currentRevision + 1);
        composition.setScenes(write(scenes));
        composition.setProducer(authService.findUserOrThrow(userId));
        Composition saved = compositions.save(composition);
        return response(project, version, saved, scenes);
    }

    @Transactional(readOnly = true)
    public RenderInput renderInput(ScriptVersion version) {
        return compositions.findByScriptVersionId(version.getId())
            .map(saved -> new RenderInput(saved, saved.getRevision(), read(saved.getScenes())))
            .orElseGet(() -> new RenderInput(null, null, scriptScenes(version)));
    }

    /**
     * The scenes a render draws, as Scene objects that are never saved: the
     * composition's when the producer has saved one, the approved script's
     * otherwise. Pre-publish QA checks these, i.e. the clip as rendered.
     */
    @Transactional(readOnly = true)
    public List<Scene> renderScenes(ScriptVersion version) {
        Composition saved = compositions.findByScriptVersionId(version.getId()).orElse(null);
        if (saved == null) {
            return scriptService.scenesOf(version);
        }
        List<Scene> scenes = new ArrayList<>();
        int order = 1;
        for (Map<String, Object> map : read(saved.getScenes())) {
            Map<String, Object> props = new LinkedHashMap<>(map);
            List.of("id", "type", "title", "narration", "durationInFrames").forEach(props::remove);
            Scene scene = Scene.builder()
                .orderNo(order++)
                .sceneType(SceneType.valueOf(String.valueOf(map.get("type"))))
                .title((String) map.get("title"))
                .narration((String) map.get("narration"))
                .durationInFrames(((Number) map.get("durationInFrames")).intValue())
                .props(writeProps(props))
                .scriptVersion(version)
                .build();
            scene.setId(String.valueOf(map.get("id")));
            scenes.add(scene);
        }
        return scenes;
    }

    /** The approved script's scenes in the SceneData shape. */
    public List<Map<String, Object>> scriptScenes(ScriptVersion version) {
        List<Map<String, Object>> scenes = new ArrayList<>();
        for (Scene scene : scriptService.scenesOf(version)) {
            scenes.add(toMap(sceneMapper.toResponse(scene), scene.getId()));
        }
        return scenes;
    }

    /**
     * Null when the producer may edit; otherwise why not. Edits happen before a
     * render, or after the reviewer sends the clip back to production; a clip
     * that is rendering, in review or approved must match its composition.
     */
    static String lockedReason(ClipProject project) {
        if (Boolean.TRUE.equals(project.getArchived())) {
            return "The project is archived";
        }
        return switch (project.getVideoStatus()) {
            case NOT_RENDERED -> null;
            case RENDERING -> "A render is running; edit the composition after it finishes";
            case IN_QA -> "The clip is in review; it can change after the reviewer sends it back to production";
            case APPROVED, PUBLISHED -> "The clip is approved; its composition is final";
        };
    }

    private static void requireApproved(ClipProject project) {
        if (project.getScriptStatus() != ScriptStatus.APPROVED) {
            throw ApiException.badRequest("Only an approved script has a production composition (script is "
                + project.getScriptStatus() + ")");
        }
    }

    private List<Map<String, Object>> normalize(List<Scene> approved, List<SceneRequest> incoming, ScriptVersion version) {
        if (incoming.size() != approved.size()) {
            throw ApiException.badRequest("The composition must keep the " + approved.size()
                + " scenes of the approved script; scenes are added or removed in a new script version");
        }
        List<Map<String, Object>> scenes = new ArrayList<>(incoming.size());
        for (int i = 0; i < incoming.size(); i++) {
            Scene original = approved.get(i);
            SceneRequest edit = incoming.get(i);
            if (!Objects.equals(edit.getId(), original.getId())) {
                throw ApiException.badRequest("Scene " + (i + 1) + " must be scene " + original.getId()
                    + " of the approved script, in the approved order");
            }
            if (edit.getType() != original.getSceneType()) {
                throw ApiException.badRequest("Scene " + (i + 1) + " keeps its approved template "
                    + original.getSceneType());
            }
            if (edit.getDurationInFrames() < 1 || edit.getDurationInFrames() > MAX_SCENE_FRAMES) {
                throw ApiException.badRequest("Scene " + (i + 1) + " duration must be 1-" + MAX_SCENE_FRAMES + " frames");
            }
            edit.getProps().remove("id");
            edit.setOrderNo(null);
            // Same props validation as the script editor; the entity is never saved.
            Scene checked = sceneMapper.toEntity(edit, i + 1, version);
            scenes.add(toMap(sceneMapper.toResponse(checked), original.getId()));
        }
        return scenes;
    }

    private static Map<String, Object> toMap(SceneResponse scene, String id) {
        Map<String, Object> map = new LinkedHashMap<>(scene.getFlattenedProps());
        map.put("id", id);
        map.put("type", scene.getType().name());
        map.put("title", scene.getTitle());
        map.put("narration", scene.getNarration());
        map.put("durationInFrames", scene.getDurationInFrames());
        return map;
    }

    private CompositionResponse response(ClipProject project, ScriptVersion version, Composition saved,
                                         List<Map<String, Object>> scenes) {
        return new CompositionResponse(
            project.getId(),
            version.getVersionNo(),
            saved == null ? 0 : saved.getRevision(),
            lockedReason(project) == null,
            lockedReason(project),
            scenes,
            saved == null ? null : saved.getUpdatedAt(),
            saved == null || saved.getProducer() == null ? null : saved.getProducer().getFullName()
        );
    }

    private List<Map<String, Object>> read(String json) {
        try {
            return objectMapper.readValue(json, SCENES_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored composition is not a scene list", ex);
        }
    }

    private String writeProps(Map<String, Object> props) {
        try {
            return objectMapper.writeValueAsString(props);
        } catch (Exception ex) {
            throw new IllegalStateException("Composition scene props could not be serialised", ex);
        }
    }

    private String write(List<Map<String, Object>> scenes) {
        try {
            return objectMapper.writeValueAsString(scenes);
        } catch (Exception ex) {
            throw ApiException.badRequest("Composition could not be serialised: " + ex.getMessage());
        }
    }
}
