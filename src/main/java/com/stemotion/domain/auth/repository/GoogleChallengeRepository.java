package com.stemotion.domain.auth.repository;
import com.stemotion.domain.auth.entity.GoogleChallenge;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;

public interface GoogleChallengeRepository extends JpaRepository<GoogleChallenge,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from GoogleChallenge c where c.id = :id")
    Optional<GoogleChallenge> lockById(@Param("id") String id);
    @Modifying @Query("delete from GoogleChallenge c where c.expiresAt < :before")
    void deleteExpired(@Param("before") LocalDateTime before);
}
