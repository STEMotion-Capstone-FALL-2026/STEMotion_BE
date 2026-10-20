package com.stemotion.core.security;

import com.stemotion.domain.auth.repository.UserRepository;
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
    final UserRepository users = mock(UserRepository.class);
    final RenderJobRepository jobs = mock(RenderJobRepository.class);
    final ResourceAccessInterceptor interceptor = new ResourceAccessInterceptor(access, users, jobs,
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
    @Test void archivingNeedsMembershipOnlySoALeaderOfAnyRoleReachesTheArchiveCheck() {
        check("DELETE", "/api/v1/scripts/one", Map.of("id", "one"));
        verify(access).requireProject("one", "writer-id", false);
    }
    @Test void guardsNestedVideoActionsAsProjectAccess() {
        check("POST", "/api/v1/scripts/one/approve-video", Map.of("projectId", "one"));
        verify(access).requireProject("one", "writer-id", false);
    }
    @Test void guardsWorkspaceMemberManagement() {
        check("POST", "/api/v1/workspaces/one/members", Map.of("id", "one"));
        verify(access).requireWorkspace("one", "writer-id", true);
    }
    @Test void listingWorkspaceMembersRequiresMembershipOnly() {
        check("GET", "/api/v1/workspaces/one/members", Map.of("id", "one"));
        verify(access).requireWorkspace("one", "writer-id", false);
    }
    @Test void creationRequiresMembershipButNotWorkspaceAdmin() {
        check("POST", "/api/v1/workspaces/one/scripts", Map.of("workspaceId", "one"));
        verify(access).requireWorkspace("one", "writer-id", false);
    }
    @Test void pollingJobChecksItsWorkspaceThroughProject() {
        when(jobs.findProjectIdById("job")).thenReturn(Optional.of("one"));
        check("GET", "/api/v1/render/status/job", Map.of("jobId", "job"));
        verify(access).requireProject("one", "writer-id", false);
    }

    @Test void draftingAndApproachesRequireActiveWriterMembership() {
        check("POST", "/api/v1/ai/script/generate", Map.of());
        check("POST", "/api/v1/ai/script/approaches", Map.of());
        verify(access, times(2)).requireAiAccess("writer-id");
    }
    @Test void alignmentRetainsItsOwnControllerGuards() {
        check("POST", "/api/v1/ai/transcribe/align", Map.of());
        check("POST", "/api/v1/ai/internal/transcribe/align", Map.of());
        // Only the password-change gate reads the (request-cached) user; no AI or project check.
        verify(access, never()).requireAiAccess(anyString());
        verify(access, never()).requireProject(anyString(), anyString(), anyBoolean());
    }
}
