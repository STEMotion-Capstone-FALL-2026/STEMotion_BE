package com.stemotion.domain.production.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.core.enums.StemEnums.RenderResolution;
import com.stemotion.core.enums.StemEnums.RenderStatus;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.production.dto.ProductionDtos.RenderCallbackRequest;
import com.stemotion.domain.production.dto.ProductionDtos.RenderJobResponse;
import com.stemotion.domain.production.dto.ProductionDtos.StartRenderRequest;
import com.stemotion.domain.production.entity.Composition;
import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.integration.client.RenderServiceClient;
import com.stemotion.integration.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Producer-side orchestration: hand an approved script to the Remotion worker
 * pool and track progress. Rendering itself happens in the Node service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenderService {

    /** The Remotion composition that stitches the scene list into one clip. */
    private static final String COMPOSITION_ID = "FullSTEMVideo";

    private final RenderJobRepository renderJobRepository;
    private final ClipProjectRepository projectRepository;
    private final ScriptService scriptService;
    private final CompositionService compositionService;
    private final ObjectMapper objectMapper;
    private final RenderServiceClient renderClient;
    private final AuthService authService;
    private final IntegrationProperties properties;
    private final ClipArtifactService clipArtifactService;

    /**
     * Queues a render of the producer's composition of the approved script,
     * or of the approved script itself when the producer has not edited it.
     *
     * <p>Only an approved script may be rendered: producing a clip from an
     * unreviewed script would put unchecked content in front of students.
     */
    @Transactional
    public RenderJobResponse start(String projectId, StartRenderRequest request, String currentUserId) {
        ClipProject project = requireRenderable(projectId);
        ScriptVersion version = scriptService.currentVersion(project);
        CompositionService.RenderInput input = compositionService.renderInput(version);
        if (input.scenes().isEmpty()) {
            throw ApiException.badRequest("The approved script has no scenes to render");
        }
        return enqueueNew(project, version, toScriptProps(project, input.scenes()), input.composition(),
            input.revision(), request.ttsVoice(), currentUserId);
    }

    private ClipProject requireRenderable(String projectId) {
        ClipProject project = scriptService.getOrThrow(projectId);
        scriptService.requireActive(project);

        if (project.getScriptStatus() != ScriptStatus.APPROVED) {
            throw ApiException.badRequest(
                "Script is " + project.getScriptStatus() + "; only an approved script can be rendered"
            );
        }
        if (project.getVideoStatus() == VideoStatus.RENDERING) {
            throw ApiException.conflict("A render is already running for this project");
        }
        return project;
    }

    private RenderJobResponse enqueueNew(ClipProject project, ScriptVersion version, Map<String, Object> scriptProps,
                                         Composition composition, Integer compositionRevision, String ttsVoice,
                                         String currentUserId) {
        // Proposal output contract: clamp legacy requests to 1080p30.
        RenderResolution resolution = RenderResolution.RES_1080P;
        int fps = 30;

        RenderJob job = renderJobRepository.save(RenderJob.builder()
            .project(project)
            .scriptVersion(version)
            .requestedBy(authService.findUserOrThrow(currentUserId))
            .status(RenderStatus.QUEUED)
            .progress(0)
            .stage("Queued")
            .resolution(resolution)
            .fps(fps)
            .ttsVoice(ttsVoice)
            .attemptCount(1)
            .composition(composition)
            .compositionRevision(compositionRevision)
            .inputProps(writeProps(scriptProps))
            .build());

        project.setVideoStatus(VideoStatus.RENDERING);
        projectRepository.save(project);

        String jobId = job.getId();
        String projectId = project.getId();
        Runnable dispatch = () -> dispatch(jobId, projectId, scriptProps, resolution, fps, ttsVoice);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // The worker reports progress against this job id at once, so it must not hear about
            // the job before the row is committed (the first callback used to get a 404).
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch.run(); }
            });
            return RenderJobResponse.from(job, null);
        }
        RenderServiceClient.EnqueueResult result = renderClient.enqueue(
            jobId, COMPOSITION_ID, scriptProps, resolution.getWidth(), resolution.getHeight(), fps, ttsVoice);
        job.setExternalJobId(result.externalJobId());
        renderJobRepository.save(job);
        return RenderJobResponse.from(job, result.estimatedSeconds());
    }

    /**
     * Hands a committed job to the worker. Runs after the request's transaction, so a worker that
     * cannot be reached marks the job failed and frees the project instead of failing the request.
     */
    private void dispatch(String jobId, String projectId, Map<String, Object> scriptProps,
                          RenderResolution resolution, int fps, String ttsVoice) {
        try {
            RenderServiceClient.EnqueueResult result = renderClient.enqueue(
                jobId, COMPOSITION_ID, scriptProps, resolution.getWidth(), resolution.getHeight(), fps, ttsVoice);
            renderJobRepository.setExternalJobId(jobId, result.externalJobId());
        } catch (RuntimeException ex) {
            log.error("Render job {} could not be handed to the worker: {}", jobId, ex.getMessage());
            if (renderJobRepository.failQueued(jobId, "Render worker unreachable: " + ex.getMessage(),
                    RenderStatus.QUEUED, RenderStatus.FAILED) > 0) {
                projectRepository.findById(projectId).ifPresent(project -> {
                    if (project.getVideoStatus() == VideoStatus.RENDERING) {
                        project.setVideoStatus(VideoStatus.NOT_RENDERED);
                        projectRepository.save(project);
                    }
                });
            }
        }
    }

    @Transactional(readOnly = true)
    public RenderJobResponse getStatus(String jobId) {
        return RenderJobResponse.from(getOrThrow(jobId), null);
    }

    @Transactional(readOnly = true)
    public List<RenderJobResponse> findByProject(String projectId) {
        scriptService.getOrThrow(projectId);
        return renderJobRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(job -> RenderJobResponse.from(job, null,
                job.getScriptVersion() == null ? null : job.getScriptVersion().getVersionNo()))
            .toList();
    }

    /**
     * Applies a progress update from the worker. A completed clip moves into QA
     * so a reviewer sees it before it can be published.
     *
     * <p>The caller (RenderController) extracts the {@code X-Render-Api-Key}
     * header and passes it here for validation against the configured shared
     * secret. This prevents external actors from spoofing render progress or
     * injecting malicious video URLs.
     */
    @Transactional
    public RenderJobResponse applyCallback(String apiKey, RenderCallbackRequest callback) {
        validateRenderApiKey(apiKey);

        RenderJob job = renderJobRepository.findById(callback.jobId())
            .or(() -> renderJobRepository.findByExternalJobId(callback.jobId()))
            .orElseThrow(() -> ApiException.notFound("RenderJob", callback.jobId()));

        // Duplicate completion and late progress must never undo QA approval,
        // cancellation or a terminal failure.
        if (job.getStatus() == RenderStatus.COMPLETED || job.getStatus() == RenderStatus.FAILED) {
            return RenderJobResponse.from(job, null);
        }
        if (callback.status() == RenderStatus.COMPLETED
                && (callback.videoUrl() == null || callback.videoUrl().isBlank())) {
            throw ApiException.badRequest("A completed render must include its video URL");
        }

        if (callback.status() != null) {
            job.setStatus(callback.status());
        }
        if (callback.progress() != null) {
            job.setProgress(Math.max(job.getProgress(), Math.max(0, Math.min(100, callback.progress()))));
        }
        if (callback.stage() != null) {
            job.setStage(callback.stage());
        }
        if (callback.videoUrl() != null) {
            job.setVideoUrl(callback.videoUrl());
        }
        if (callback.errorMessage() != null) {
            job.setErrorMessage(callback.errorMessage());
        }
        renderJobRepository.save(job);

        ClipProject project = job.getProject();
        if (job.getStatus() == RenderStatus.COMPLETED) {
            job.setProgress(100);
            clipArtifactService.recordCompleted(job, callback.captionSync());
            project.setVideoUrl(job.getVideoUrl());
            project.setVideoStatus(VideoStatus.IN_QA);
            projectRepository.save(project);
        } else if (job.getStatus() == RenderStatus.FAILED) {
            return RenderJobResponse.from(handleFailure(job), null);
        }

        return RenderJobResponse.from(job, null);
    }

    /**
     * Requeues a failed render automatically, up to the attempt budget from the
     * project proposal (three tries). Only the final failure is escalated, so a
     * transient worker crash never needs a human to press retry.
     *
     * <p>The retry reuses the same job row and increments its attempt counter,
     * which keeps one render job per producer request rather than scattering
     * the history across rows.
     */
    private RenderJob handleFailure(RenderJob job) {
        ClipProject project = job.getProject();
        int maxAttempts = properties.getRender().getMaxAttempts();

        if (job.getAttemptCount() >= maxAttempts) {
            log.error("Render job {} gave up after {} attempts: {}",
                job.getId(), job.getAttemptCount(), job.getErrorMessage());
            project.setVideoStatus(VideoStatus.NOT_RENDERED);
            projectRepository.save(project);
            return renderJobRepository.save(job);
        }

        int nextAttempt = job.getAttemptCount() + 1;
        log.warn("Render job {} failed on attempt {} ({}), retrying as attempt {}",
            job.getId(), job.getAttemptCount(), job.getErrorMessage(), nextAttempt);

        try {
            RenderServiceClient.EnqueueResult result = renderClient.enqueue(
                job.getId(),
                COMPOSITION_ID,
                propsOf(job),
                1920,
                1080,
                30,
                job.getTtsVoice()
            );

            job.setResolution(RenderResolution.RES_1080P);
            job.setFps(30);
            job.setExternalJobId(result.externalJobId());
            job.setStatus(RenderStatus.QUEUED);
            job.setProgress(0);
            job.setStage("Thử lại lần " + nextAttempt + "/" + maxAttempts);
            job.setAttemptCount(nextAttempt);
            // The clip is on its way again, so the project stays in production.
            project.setVideoStatus(VideoStatus.RENDERING);
            projectRepository.save(project);
        } catch (Exception ex) {
            // The render service is unreachable; stop here rather than spinning.
            log.error("Render job {} could not be requeued: {}", job.getId(), ex.getMessage());
            job.setAttemptCount(nextAttempt);
            job.setErrorMessage(job.getErrorMessage() + " | retry failed: " + ex.getMessage());
            project.setVideoStatus(VideoStatus.NOT_RENDERED);
            projectRepository.save(project);
        }

        return renderJobRepository.save(job);
    }

    /** Re-queues a failed job manually, outside the automatic budget. */
    @Transactional
    public RenderJobResponse retry(String jobId, String currentUserId) {
        RenderJob failed = getOrThrow(jobId);
        if (failed.getStatus() != RenderStatus.FAILED) {
            throw ApiException.badRequest("Only a failed job can be retried");
        }
        if (failed.getAttemptCount() >= properties.getRender().getMaxAttempts()) {
            throw ApiException.badRequest(
                "Render job has already used its " + properties.getRender().getMaxAttempts() + " attempts"
            );
        }

        // Same composition version as the failed job, even if the draft changed since.
        ClipProject project = requireRenderable(failed.getProject().getId());
        RenderJobResponse response = enqueueNew(project, failed.getScriptVersion(), propsOf(failed),
            failed.getComposition(), failed.getCompositionRevision(), failed.getTtsVoice(), currentUserId);

        RenderJob retried = getOrThrow(response.jobId());
        retried.setAttemptCount(failed.getAttemptCount() + 1);
        renderJobRepository.save(retried);

        return RenderJobResponse.from(retried, response.estimatedSeconds());
    }

    @Transactional
    public void cancel(String jobId) {
        RenderJob job = getOrThrow(jobId);
        if (job.getStatus() == RenderStatus.COMPLETED) {
            throw ApiException.badRequest("A completed render cannot be cancelled");
        }
        if (job.getExternalJobId() != null) {
            renderClient.cancel(job.getExternalJobId());
        }
        job.setStatus(RenderStatus.FAILED);
        job.setErrorMessage("Cancelled by user");
        renderJobRepository.save(job);

        ClipProject project = job.getProject();
        project.setVideoStatus(VideoStatus.NOT_RENDERED);
        projectRepository.save(project);
    }

    private RenderJob getOrThrow(String jobId) {
        return renderJobRepository.findById(jobId)
            .orElseThrow(() -> ApiException.notFound("RenderJob", jobId));
    }

    /**
     * Validates the render worker API key against the configured shared secret.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @throws ApiException 401 if the key is missing or blank
     * @throws ApiException 403 if the key does not match
     */
    private void validateRenderApiKey(String apiKey) {
        String expectedKey = properties.getRender().getApiKey();
        if (expectedKey == null || expectedKey.isBlank()) {
            log.error("RENDER_SERVICE_KEY is not configured — rejecting render callback");
            throw ApiException.unauthorized("Render API key is not configured on the server");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.unauthorized("Missing X-Render-Api-Key header");
        }
        if (!java.security.MessageDigest.isEqual(
                expectedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw ApiException.forbidden("Invalid render API key");
        }
    }

    /**
     * Builds the STEMScript object the FullSTEMVideo composition takes as its
     * {@code script} prop — the same shape the Remotion Player previews in the
     * front end, so preview and render stay identical.
     */
    private Map<String, Object> toScriptProps(ClipProject project, List<Map<String, Object>> scenes) {
        List<Map<String, Object>> sceneProps = new ArrayList<>(scenes.size());
        int sourceFps = project.getFps() == null || project.getFps() <= 0 ? 30 : project.getFps();
        for (Map<String, Object> scene : scenes) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", scene.get("id"));
            payload.put("type", scene.get("type"));
            payload.put("title", scene.get("title"));
            payload.put("narration", scene.get("narration"));
            long frames = ((Number) scene.get("durationInFrames")).longValue();
            payload.put("durationInFrames", Math.toIntExact(Math.max(1, Math.round(frames * 30.0 / sourceFps))));
            scene.forEach(payload::putIfAbsent);
            sceneProps.add(payload);
        }

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("id", project.getId());
        // Seeds the shared look of every clip of the workspace (FE flat/variation.ts).
        script.put("workspaceId", project.getWorkspace() == null ? null : project.getWorkspace().getId());
        script.put("title", project.getTitle());
        script.put("subject", project.getSubject().name());
        script.put("gradeLevel", project.getGradeLevel());
        script.put("totalDurationSeconds", project.getTotalDurationSeconds());
        script.put("scriptStatus", project.getScriptStatus().name());
        script.put("videoStatus", project.getVideoStatus().name());
        script.put("fps", 30);
        script.put("scenes", sceneProps);
        script.put("createdAt", String.valueOf(project.getCreatedAt()));
        return script;
    }

    /** The props a job was sent; jobs from before V19 are rebuilt from their script version. */
    private Map<String, Object> propsOf(RenderJob job) {
        if (job.getInputProps() != null) {
            try {
                return objectMapper.readValue(job.getInputProps(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                throw new IllegalStateException("Render job " + job.getId() + " has unreadable input props", ex);
            }
        }
        return toScriptProps(job.getProject(), compositionService.scriptScenes(job.getScriptVersion()));
    }

    private String writeProps(Map<String, Object> props) {
        try {
            return objectMapper.writeValueAsString(props);
        } catch (Exception ex) {
            throw new IllegalStateException("Render props could not be serialised", ex);
        }
    }
}
