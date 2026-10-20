package com.stemotion.core.security;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.domain.auth.entity.User;

/** A user's seat in a workspace, read as plain values so it can be reused across a request. */
public record Membership(String workspaceId, User.UserRole role, MemberStatus status, boolean leader) {
    public boolean active() {
        return status == MemberStatus.ACTIVE;
    }
}
