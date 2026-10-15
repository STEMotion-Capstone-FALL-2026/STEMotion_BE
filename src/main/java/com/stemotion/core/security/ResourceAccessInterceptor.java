package com.stemotion.core.security;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.script.repository.SceneRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
    private final RenderJobRepository jobs;
    private final SceneRepository scenes;
    private final FeedbackCommentRepository comments;

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) return true;
        var variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null || variables.isEmpty()) return true;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw ApiException.unauthorized("Authentication required");
        }
        String userId = auth.getName();
        String path = request.getRequestURI();
        boolean write = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
        if (path.startsWith("/api/v1/workspaces/")) {
            String workspace = variables.getOrDefault("workspaceId", variables.get("id"));
            if (workspace != null) access.requireWorkspace(workspace, userId,
                write && !path.endsWith("/scripts"));
        }
        String project = variables.get("projectId");
        if (project == null && path.startsWith("/api/v1/scripts/")) project = variables.get("id");
        if (project != null) access.requireProject(project, userId,
            write && ("PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod()))
            && path.matches("/api/v1/scripts/[^/]+"));
        String job = variables.get("jobId");
        if (job != null) access.requireProject(jobs.findById(job)
            .orElseThrow(() -> ApiException.notFound("RenderJob", job)).getProject().getId(), userId, false);
        String scene = variables.get("sceneId");
        if (scene != null) access.requireProject(scenes.findById(scene)
            .orElseThrow(() -> ApiException.notFound("Scene", scene)).getScriptVersion().getProject().getId(), userId, false);
        String comment = variables.get("commentId");
        if (comment != null) access.requireProject(comments.findById(comment)
            .orElseThrow(() -> ApiException.notFound("FeedbackComment", comment)).getProject().getId(), userId, false);
        return true;
    }
}
