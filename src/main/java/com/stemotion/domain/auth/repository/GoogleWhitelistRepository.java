package com.stemotion.domain.auth.repository;
import com.stemotion.domain.auth.entity.GoogleWhitelist;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface GoogleWhitelistRepository extends JpaRepository<GoogleWhitelist,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from GoogleWhitelist w where w.email = :email")
    Optional<GoogleWhitelist> lockByEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from GoogleWhitelist w where w.id = :id")
    Optional<GoogleWhitelist> lockById(@Param("id") String id);
    Optional<GoogleWhitelist> findByEmail(String email);
    @EntityGraph(attributePaths={"identity", "identity.user"})
    @Query("select w from GoogleWhitelist w where (:allStatuses = true or w.status = :status) and w.email like concat('%', :query, '%')")
    Page<GoogleWhitelist> search(@Param("query") String query, @Param("allStatuses") boolean allStatuses,
                                @Param("status") String status, Pageable pageable);
}
