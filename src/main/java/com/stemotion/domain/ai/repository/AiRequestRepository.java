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

    /** Money spent on AI calls since a moment (the monthly budget, FR-44). */
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(a.monetaryCost), 0) from AiRequest a where a.createdAt >= :since")
    java.math.BigDecimal spentSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    /** Money spent on AI calls for one project (the per-clip cap, FR-44). */
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(a.monetaryCost), 0) from AiRequest a where a.projectId = :projectId")
    java.math.BigDecimal spentOnProject(@org.springframework.data.repository.query.Param("projectId") String projectId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AiRequest a where a.id = :id")
    java.util.Optional<AiRequest> findForDecision(@org.springframework.data.repository.query.Param("id") String id);
}
