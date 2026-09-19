package com.stemotion.domain.workspace.repository;

import com.stemotion.domain.workspace.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, String> {

    List<WorkspaceMember> findByWorkspaceIdOrderByCreatedAtAsc(String workspaceId);

    List<WorkspaceMember> findByUserId(String userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndInvitedEmail(String workspaceId, String invitedEmail);

    boolean existsByWorkspaceIdAndUserId(String workspaceId, String userId);

    long countByWorkspaceId(String workspaceId);
}
