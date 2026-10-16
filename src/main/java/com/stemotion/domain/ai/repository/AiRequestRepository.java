package com.stemotion.domain.ai.repository;

import com.stemotion.domain.ai.entity.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestRepository extends JpaRepository<AiRequest,String> {
    /** Totals over every recorded AI call, for the admin cost and usage panel. */
    interface UsageTotals {
        long getCalls();
        Double getAvgLatencyMs();
        Long getTokens();
        java.math.BigDecimal getCost();
    }

    @org.springframework.data.jpa.repository.Query("""
        select count(a) as calls,
               avg(a.latencyMs) as avgLatencyMs,
               sum(coalesce(a.costTokens, coalesce(a.inputTokens, 0) + coalesce(a.outputTokens, 0))) as tokens,
               sum(a.monetaryCost) as cost
        from AiRequest a
        """)
    UsageTotals usageTotals();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AiRequest a where a.id = :id")
    java.util.Optional<AiRequest> findForDecision(@org.springframework.data.repository.query.Param("id") String id);
}
