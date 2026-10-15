package com.stemotion.domain.admin.repository;

import com.stemotion.domain.ai.entity.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

public interface AiUsageRepository extends JpaRepository<AiRequest, String> {
    interface UsageRow {
        LocalDate getUsageDay(); String getModel(); String getUserId();
        Long getRequestCount(); Long getTokensIn(); Long getTokensOut();
        Long getUnknownTokenRequests(); Long getUnknownCostRequests(); Long getFailedRequests();
        BigDecimal getKnownCostUsd();
    }
    String FILTER = """
        FROM ai_requests a WHERE a.created_at >= :fromTime AND a.created_at < :untilTime
        AND (CAST(:model AS text) IS NULL OR a.model = CAST(:model AS text))
        AND (CAST(:userId AS text) IS NULL OR CAST(a.requester_id AS text) = CAST(:userId AS text))
        """;
    String GROUP = " GROUP BY 1, 2, 3 ";
    @Query(value = """
        SELECT CAST(a.created_at AT TIME ZONE :zone AS date) AS usageDay, a.model AS model,
        CAST(a.requester_id AS text) AS userId, count(*) AS requestCount,
        sum(CAST(a.input_tokens AS bigint)) AS tokensIn, sum(CAST(a.output_tokens AS bigint)) AS tokensOut,
        count(*) FILTER (WHERE a.input_tokens IS NULL OR a.output_tokens IS NULL) AS unknownTokenRequests,
        count(*) FILTER (WHERE a.monetary_cost IS NULL OR a.cost_currency IS DISTINCT FROM 'USD') AS unknownCostRequests,
        count(*) FILTER (WHERE a.call_status = 'FAILED') AS failedRequests,
        sum(a.monetary_cost) FILTER (WHERE a.cost_currency = 'USD') AS knownCostUsd
        """ + FILTER + GROUP + " ORDER BY usageDay DESC, a.model ASC, CAST(a.requester_id AS text) ASC NULLS LAST ",
        countQuery = "SELECT count(*) FROM (SELECT CAST(a.created_at AT TIME ZONE :zone AS date), a.model, a.requester_id " + FILTER + GROUP + ") grouped", nativeQuery = true)
    Page<UsageRow> aggregate(@Param("fromTime") Instant from, @Param("untilTime") Instant until,
        @Param("zone") String zone, @Param("model") String model, @Param("userId") String userId, Pageable page);
}
