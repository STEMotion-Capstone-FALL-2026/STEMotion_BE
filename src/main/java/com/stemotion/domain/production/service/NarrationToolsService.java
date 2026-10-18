package com.stemotion.domain.production.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.production.dto.ProductionDtos.SyncDurationsResponse;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.integration.client.RenderServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Narration tools of the studio that need the render worker's TTS and Whisper: listening to a
 * scene's narration (BE-P0-3) and timing scenes from real audio (BE-P0-2). The browser talks to
 * the API with its JWT; only the API holds the worker key.
 */
@Service
@RequiredArgsConstructor
public class NarrationToolsService {

    static final int MAX_PREVIEW_CHARS = 1500;

    private final ScriptService scriptService;
    private final CompositionService compositionService;
    private final RenderServiceClient renderClient;
    private final WorkspaceAccess workspaceAccess;
    private final ObjectMapper objectMapper;

    /** Audio of the given text, or of the scene's narration when no text is given. */
    public byte[] ttsPreview(String projectId, String text, String sceneId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        String spoken = text;
        if ((spoken == null || spoken.isBlank()) && sceneId != null) {
            spoken = narrationOf(project, sceneId);
        }
        if (spoken == null || spoken.isBlank()) {
            throw ApiException.badRequest("Give the text to read or a scene with narration");
        }
        spoken = spoken.trim();
        if (spoken.length() > MAX_PREVIEW_CHARS) {
            throw ApiException.badRequest("A preview reads at most " + MAX_PREVIEW_CHARS + " characters; preview one scene at a time");
        }
        return renderClient.ttsPreview(spoken);
    }

    /**
     * Times every scene from its TTS audio. A draft (writer) gets its scene rows updated; an
     * approved script's composition (producer) gets a new revision with captionWords as well.
     */
    public SyncDurationsResponse syncDurations(String projectId, Integer baseRevision, String userId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        scriptService.requireActive(project);
        ScriptVersion version = scriptService.currentVersion(project);
        boolean production = project.getScriptStatus() == ScriptStatus.APPROVED;
        if (production) {
            workspaceAccess.requireProjectRole(projectId, userId, Set.of(User.UserRole.PRODUCER));
            if (baseRevision == null) {
                throw ApiException.badRequest("baseRevision is required to sync a production composition");
            }
            String locked = CompositionService.lockedReason(project);
            if (locked != null) throw ApiException.conflict(locked);
        } else {
            workspaceAccess.requireProjectRole(projectId, userId, Set.of(User.UserRole.WRITER));
            if (project.getScriptStatus() == ScriptStatus.IN_REVIEW) {
                throw ApiException.badRequest("The script is in review; sync durations on the draft or after approval");
            }
        }

        int fps = project.getFps() == null || project.getFps() <= 0 ? 30 : project.getFps();
        List<Map<String, Object>> scenes = production
            ? compositionService.renderInput(version).scenes()
            : compositionService.scriptScenes(version);
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("id", project.getId());
        script.put("fps", fps);
        script.put("scenes", scenes);
        JsonNode synced = renderClient.syncDurations(script, fps);

        Map<String, Integer> frames = new HashMap<>();
        Map<String, Object> captions = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode scene : synced.path("script").path("scenes")) {
            String id = scene.path("id").asText(null);
            if (id == null || !scene.path("durationInFrames").canConvertToInt()) continue;
            frames.put(id, scene.path("durationInFrames").asInt());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("durationInFrames", scene.path("durationInFrames").asInt());
            if (scene.path("captionWords").isArray()) {
                Object words = objectMapper.convertValue(scene.path("captionWords"), List.class);
                captions.put(id, words);
                entry.put("captionWords", words);
            }
            result.add(entry);
        }
        if (frames.isEmpty()) {
            throw ApiException.badRequest("The script has no narrated scenes to time");
        }
        JsonNode captionSync = synced.path("captionSync").isObject() ? synced.get("captionSync") : null;

        if (production) {
            var saved = compositionService.applyMeasured(projectId, baseRevision, frames, captions, userId);
            int totalFrames = saved.scenes().stream()
                .mapToInt(scene -> ((Number) scene.get("durationInFrames")).intValue()).sum();
            return new SyncDurationsResponse("COMPOSITION", saved.revision(),
                Math.round(totalFrames / (float) fps), result, captionSync);
        }
        var draft = scriptService.applyMeasuredDurations(projectId, frames);
        return new SyncDurationsResponse("SCRIPT", null, draft.totalDurationSeconds(), result, captionSync);
    }

    private String narrationOf(ClipProject project, String sceneId) {
        ScriptVersion version = scriptService.currentVersion(project);
        if (project.getScriptStatus() == ScriptStatus.APPROVED) {
            for (Map<String, Object> scene : compositionService.renderInput(version).scenes()) {
                if (sceneId.equals(String.valueOf(scene.get("id")))) return (String) scene.get("narration");
            }
        }
        for (Scene scene : scriptService.scenesOf(version)) {
            if (sceneId.equals(scene.getId())) return scene.getNarration();
        }
        throw ApiException.notFound("Scene", sceneId);
    }
}
