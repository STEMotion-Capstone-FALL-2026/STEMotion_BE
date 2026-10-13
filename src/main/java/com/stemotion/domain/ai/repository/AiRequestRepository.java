package com.stemotion.domain.ai.repository;

import com.stemotion.domain.ai.entity.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestRepository extends JpaRepository<AiRequest,String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AiRequest a where a.id = :id")
    java.util.Optional<AiRequest> findForDecision(@org.springframework.data.repository.query.Param("id") String id);
}
