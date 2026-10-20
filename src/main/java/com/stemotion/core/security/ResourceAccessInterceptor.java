package com.stemotion.core.security;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.script.repository.SceneRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/** Resource authorization shared by all MVC endpoints, independent of role checks. */
@Component
@RequiredArgsConstructor
public class ResourceAccessInterceptor implements HandlerInterceptor {
    private final WorkspaceAccess access;
    private final UserRepository users;
    private final RenderJobRepository jobs;
    private final SceneRepository scenes;
    private final FeedbackCommentRepository comments;

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) return true;

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/render/callback") || path.startsWith("/actuator")
                || path.equals("/api/v1/ai/internal/transcribe/align")) {
            return true;
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return true;
        }

        String userId = auth.getName();

        // Must change password check: block all other endpoints until password is changed
        if (!path.equals("/api/v1/auth/me") && !path.equals("/api/v1/auth/change-password")) {
            access.user(userId).ifPresent(user -> {
                if (user.isMustChangePassword()) {
                    throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "PASSWORD_CHANGE_REQUIRED",
                        "Password change is required before accessing other resources"
                    );
                }
            });
        }

        // AI endpoints quota protection: requires active WRITER role in at least one workspace or ADMIN
        if (path.startsWith("/api/v1/ai/") && !path.equals("/api/v1/ai/health")
                && !path.equals("/api/v1/ai/transcribe/align")) {
            access.requireAiAccess(userId);
        }

        var variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null || variables.isEmpty()) return true;

        boolean write = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
        if (path.startsWith("/api/v1/workspaces/")) {
            String workspace = variables.getOrDefault("workspaceId", variables.get("id"));
            if (workspace != null) {
                boolean isManageMembers = path.contains("/candidates") || (write && path.contains("/members"));
                access.requireWorkspace(workspace, userId, isManageMembers || (write && !path.endsWith("/scripts")));
            }
        }
        String project = variables.get("projectId");
        if (project == null && path.startsWith("/api/v1/scripts/")) project = variables.get("id");
        // Editing the draft needs the WRITER role. Archiving (DELETE) only needs membership here:
        // canArchiveProject lets the owner, the leader (whatever their role) and admins do it.
        if (project != null) access.requireProject(project, userId,
            "PUT".equals(request.getMethod()) && path.matches("/api/v1/scripts/[^/]+"));
        String job = variables.get("jobId");
        if (job != null) access.requireProject(jobs.findProjectIdById(job)
            .orElseThrow(() -> ApiException.notFound("RenderJob", job)), userId, false);
        String scene = variables.get("sceneId");
        if (scene != null) access.requireProject(scenes.findProjectIdById(scene)
            .orElseThrow(() -> ApiException.notFound("Scene", scene)), userId, false);
        String comment = variables.get("commentId");
        if (comment != null) access.requireProject(comments.findProjectIdById(comment)
            .orElseThrow(() -> ApiException.notFound("FeedbackComment", comment)), userId, false);
        return true;
    }
}
