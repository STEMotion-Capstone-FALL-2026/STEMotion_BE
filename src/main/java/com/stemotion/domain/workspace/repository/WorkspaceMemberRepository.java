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

    /** A user's seats as plain values (one query, nothing lazy), for access checks. */
    @org.springframework.data.jpa.repository.Query("select new com.stemotion.core.security.Membership("
        + "m.workspace.id, m.role, m.status, m.isLeader) from WorkspaceMember m where m.user.id = :userId")
    List<com.stemotion.core.security.Membership> findMembershipsByUserId(
        @org.springframework.data.repository.query.Param("userId") String userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, String userId);

    boolean existsByWorkspaceIdAndUserId(String workspaceId, String userId);

    boolean existsByWorkspaceIdAndUserIdAndStatus(String workspaceId, String userId, MemberStatus status);

    long countByWorkspaceIdAndIsLeaderTrueAndStatus(String workspaceId, MemberStatus status);

    long countByWorkspaceId(String workspaceId);

    /** Member count per workspace in one grouped query: rows of (workspaceId, count). */
    @org.springframework.data.jpa.repository.Query("select m.workspace.id, count(m) from WorkspaceMember m where m.workspace.id in :ids group by m.workspace.id")
    List<Object[]> countByWorkspaceIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

    /** The user's seats with their workspace, in one query. */
    @org.springframework.data.jpa.repository.Query("select m from WorkspaceMember m join fetch m.workspace where m.user.id = :userId")
    List<WorkspaceMember> findWithWorkspaceByUserId(@org.springframework.data.repository.query.Param("userId") String userId);

    /** A workspace's members with their user account, in one query. */
    @org.springframework.data.jpa.repository.Query("select m from WorkspaceMember m join fetch m.user where m.workspace.id = :workspaceId order by m.createdAt asc")
    List<WorkspaceMember> findWithUserByWorkspaceId(@org.springframework.data.repository.query.Param("workspaceId") String workspaceId);
}
