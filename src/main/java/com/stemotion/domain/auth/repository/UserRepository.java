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

    @Query("""
        SELECT u FROM User u
        WHERE u.status = com.stemotion.domain.auth.entity.User.UserStatus.ACTIVE
          AND u.id NOT IN (
              SELECT m.user.id FROM com.stemotion.domain.workspace.entity.WorkspaceMember m WHERE m.workspace.id = :workspaceId
          )
        ORDER BY u.fullName ASC
    """)
    List<User> findCandidatesForWorkspace(@Param("workspaceId") String workspaceId);
}
