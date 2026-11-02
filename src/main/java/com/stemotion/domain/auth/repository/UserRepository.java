package com.stemotion.domain.auth.repository;

import com.stemotion.domain.auth.entity.SystemRole;

import com.stemotion.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @Query(value="select 1 from pg_advisory_xact_lock(739201105)", nativeQuery=true)
    Integer lockSystemAdministration();

    long countBySystemRoleAndStatus(SystemRole role, User.UserStatus status);

    @Query("select count(u) from User u where u.systemRole=:role and u.status=:status and "
        + "(u.passwordHash is not null or exists (select i.id from GoogleIdentity i where i.user=u and i.whitelist.status='ACTIVE'))")
    long countAdministratorsWithLogin(@Param("role") SystemRole role, @Param("status") User.UserStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value="insert into account_security_audit(actor_id,user_id,action,old_role,new_role,old_status,new_status) values (:actor,:userId,:action,:oldRole,:newRole,:oldStatus,:newStatus)", nativeQuery=true)
    void auditAdministration(@Param("actor") String actor, @Param("userId") String userId,
        @Param("action") String action, @Param("oldRole") String oldRole, @Param("newRole") String newRole,
        @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);

    @Query("select u,i,w from User u left join GoogleIdentity i on i.user = u left join i.whitelist w where u.username = :username")
    List<Object[]> authenticationByUsername(@Param("username") String username);
    default Optional<User> findByUsername(String username) { return authenticationByUsername(username).stream().findFirst().map(UserRepository::authenticationUser); }
    boolean existsByUsername(String username);

    @Query("select u,i,w from User u left join GoogleIdentity i on i.user = u left join i.whitelist w where u.id = :id")
    List<Object[]> authenticationById(@Param("id") String id);
    default Optional<User> findForAuthentication(String id) { return authenticationById(id).stream().findFirst().map(UserRepository::authenticationUser); }

    @Query("select u,i,w from User u left join GoogleIdentity i on i.user = u left join i.whitelist w where u.id in :ids")
    List<Object[]> authenticationByIds(@Param("ids") java.util.Collection<String> ids);
    default List<User> hydrateProfiles(List<User> profiles) {
        if (profiles.isEmpty()) return profiles;
        var identities = new java.util.HashMap<String,com.stemotion.domain.auth.entity.GoogleIdentity>();
        authenticationByIds(profiles.stream().map(User::getId).toList()).forEach(row -> {
            User user = authenticationUser(row);
            identities.put(user.getId(), user.getGoogleIdentity());
        });
        profiles.forEach(user -> user.setGoogleIdentity(identities.get(user.getId())));
        return profiles;
    }
    private static User authenticationUser(Object[] row) {
        User user = (User) row[0];
        var identity = (com.stemotion.domain.auth.entity.GoogleIdentity) row[1];
        if (identity != null) identity.setWhitelist((com.stemotion.domain.auth.entity.GoogleWhitelist) row[2]);
        user.setGoogleIdentity(identity);
        return user;
    }

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") String id);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    /** Accounts whose email differs only in letter case (older data has a@ and A@). */
    List<User> findAllByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

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
        return hydrateProfiles(findByStatusNotInWorkspace(workspaceId, User.UserStatus.ACTIVE));
    }
}
