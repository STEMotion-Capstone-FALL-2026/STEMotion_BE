package com.stemotion.core.security;

import com.stemotion.domain.production.entity.RenderJob;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.script.repository.SceneRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;

class ResourceAccessInterceptorTest {
    final WorkspaceAccess access = mock(WorkspaceAccess.class);
    final RenderJobRepository jobs = mock(RenderJobRepository.class);
    final ResourceAccessInterceptor interceptor = new ResourceAccessInterceptor(access, jobs,
        mock(SceneRepository.class), mock(FeedbackCommentRepository.class));
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void check(String method, String uri, Map<String, String> variables) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("writer-id", null, java.util.List.of()));
        var request = new MockHttpServletRequest(method, uri);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables);
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }
    @Test void guardsDraftWritesAsEdits() {
        check("PUT", "/api/v1/scripts/one", Map.of("id", "one"));
        verify(access).requireProject("one", "writer-id", true);
    }
    @Test void guardsNestedVideoActionsAsProjectAccess() {
        check("POST", "/api/v1/scripts/one/approve-video", Map.of("projectId", "one"));
        verify(access).requireProject("one", "writer-id", false);
    }
    @Test void guardsWorkspaceMemberManagement() {
        check("POST", "/api/v1/workspaces/one/members", Map.of("id", "one"));
        verify(access).requireWorkspace("one", "writer-id", true);
    }
    @Test void creationRequiresMembershipButNotWorkspaceAdmin() {
        check("POST", "/api/v1/workspaces/one/scripts", Map.of("workspaceId", "one"));
        verify(access).requireWorkspace("one", "writer-id", false);
    }
    @Test void pollingJobChecksItsWorkspaceThroughProject() {
        var project = new ClipProject(); project.setId("one");
        when(jobs.findById("job")).thenReturn(Optional.of(RenderJob.builder().project(project).build()));
        check("GET", "/api/v1/render/status/job", Map.of("jobId", "job"));
        verify(access).requireProject("one", "writer-id", false);
    }
}
