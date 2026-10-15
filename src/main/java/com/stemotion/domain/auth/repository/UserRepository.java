package com.stemotion.domain.auth.repository;

import com.stemotion.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    /**
     * Active accounts that are not yet members of the workspace. The status is
     * a parameter: Hibernate 6 cannot resolve a nested enum constant written
     * as a dotted path in JPQL, which stopped the application from starting.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.status = :status
          AND u.id NOT IN (
              SELECT m.user.id FROM WorkspaceMember m WHERE m.workspace.id = :workspaceId
          )
        ORDER BY u.fullName ASC
    """)
    List<User> findByStatusNotInWorkspace(@Param("workspaceId") String workspaceId, @Param("status") User.UserStatus status);

    default List<User> findCandidatesForWorkspace(String workspaceId) {
        return findByStatusNotInWorkspace(workspaceId, User.UserStatus.ACTIVE);
    }
}
