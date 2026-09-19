package com.stemotion.domain.workspace.dto;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record WorkspaceRequest(
        @NotBlank(message = "name is required")
        String name,
        String department
    ) {}

    public record WorkspaceResponse(
        String id,
        String name,
        String department,
        long membersCount,
        long activeProjects
    ) {}

    public record InviteMemberRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        @NotNull(message = "role is required")
        User.UserRole role
    ) {}

    public record MemberResponse(
        String id,
        String userId,
        String name,
        String email,
        User.UserRole role,
        MemberStatus status
    ) {
        public static MemberResponse from(WorkspaceMember member) {
            User user = member.getUser();
            return new MemberResponse(
                member.getId(),
                user == null ? null : user.getId(),
                user == null ? member.getInvitedEmail() : user.getFullName(),
                member.getInvitedEmail(),
                member.getRole(),
                member.getStatus()
            );
        }
    }

    public record UpdateMemberRequest(
        User.UserRole role,
        MemberStatus status
    ) {}
}
