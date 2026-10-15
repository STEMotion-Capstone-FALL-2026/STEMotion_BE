package com.stemotion.domain.workspace.repository;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, String> {

    List<WorkspaceMember> findByWorkspaceIdOrderByCreatedAtAsc(String workspaceId);

    List<WorkspaceMember> findByUserId(String userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, String userId);

    boolean existsByWorkspaceIdAndUserId(String workspaceId, String userId);

    boolean existsByWorkspaceIdAndUserIdAndStatus(String workspaceId, String userId, MemberStatus status);

    long countByWorkspaceIdAndIsLeaderTrueAndStatus(String workspaceId, MemberStatus status);

    long countByWorkspaceId(String workspaceId);
}
