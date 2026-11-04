package com.stemotion.domain.auth.repository;
import com.stemotion.domain.auth.entity.GoogleIdentity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface GoogleIdentityRepository extends JpaRepository<GoogleIdentity,String> {
    @Query("select i from GoogleIdentity i join fetch i.user join fetch i.whitelist where i.whitelist.id = :id")
    Optional<GoogleIdentity> findByWhitelistId(@Param("id") String id);
    @Query("select i from GoogleIdentity i join fetch i.user join fetch i.whitelist where i.user.id = :id")
    Optional<GoogleIdentity> findByUserId(@Param("id") String id);
    boolean existsBySubject(String subject);
    @Query("select count(i) > 0 from GoogleIdentity i where i.id = :id and i.user.id = :userId and i.whitelist.status = 'ACTIVE'")
    boolean isAllowed(@Param("id") String id, @Param("userId") String userId);
}
