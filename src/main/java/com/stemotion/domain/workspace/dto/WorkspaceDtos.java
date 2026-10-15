package com.stemotion.domain.workspace.dto;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record WorkspaceRequest(
        @NotBlank(message = "name is required")
        String name,
        String department,
        @NotBlank(message = "leaderUserId is required")
        String leaderUserId,
        User.UserRole leaderRole
    ) {}

    public record WorkspaceResponse(
        String id,
        String name,
        String department,
        long membersCount,
        long activeProjects,
        User.UserRole myRole,
        Boolean isLeader
    ) {}

    public record AddMemberRequest(
        @NotBlank(message = "userId is required")
        String userId,

        @NotNull(message = "role is required")
        User.UserRole role,

        Boolean leader
    ) {}

    public record UpdateMemberRequest(
        User.UserRole role,
        MemberStatus status,
        Boolean leader
    ) {}

    public record MemberResponse(
        String id,
        String userId,
        String name,
        String email,
        User.UserRole role,
        boolean isLeader,
        MemberStatus status
    ) {
        public static MemberResponse from(WorkspaceMember member) {
            User user = member.getUser();
            return new MemberResponse(
                member.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                member.getRole(),
                member.isLeader(),
                member.getStatus()
            );
        }
    }

    public record CandidateUserResponse(
        String id,
        String fullName,
        String email
    ) {
        public static CandidateUserResponse from(User user) {
            return new CandidateUserResponse(user.getId(), user.getFullName(), user.getEmail());
        }
    }
}
