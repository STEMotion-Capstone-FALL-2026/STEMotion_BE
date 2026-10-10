package com.stemotion.domain.workspace.controller;

import com.stemotion.domain.workspace.dto.WorkspaceDtos.AcceptInvitationRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.MemberResponse;
import com.stemotion.domain.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspace-invitations")
@RequiredArgsConstructor
public class WorkspaceInvitationController {
    private final WorkspaceService workspaces;

    @PostMapping("/accept")
    public MemberResponse accept(@Valid @RequestBody AcceptInvitationRequest request,
                                 @AuthenticationPrincipal UserDetails principal) {
        return workspaces.acceptInvitation(request.token(), principal.getUsername());
    }
}
