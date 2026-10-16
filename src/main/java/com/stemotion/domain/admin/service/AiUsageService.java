package com.stemotion.domain.admin.service;

import com.stemotion.core.exception.ApiException;
import com.stemotion.domain.admin.repository.AiUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.List;

@Service
public class AiUsageService {
    private final AiUsageRepository repository;
    private final ZoneId zone;
    public AiUsageService(AiUsageRepository repository, @Value("${AI_USAGE_ZONE:Asia/Ho_Chi_Minh}") String zone) {
        this.repository = repository; this.zone = ZoneId.of(zone);
    }
    public record UsageGroup(LocalDate day, String model, String userId, long requestCount,
        Long tokensIn, Long tokensOut, long unknownTokenRequests, long failedRequests,
        BigDecimal estimatedCostUsd, BigDecimal knownEstimatedCostUsd, long unknownCostRequests) {}
    public record UsagePage(List<UsageGroup> content, int page, int size, long totalElements, int totalPages,
        LocalDate from, LocalDate to, String timezone) {}
    @Transactional(readOnly=true)
    public UsagePage find(LocalDate from, LocalDate to, String model, String userId, int page, int size) {
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (page < 0 || page > 100000 || size < 1 || size > 100 || start.isAfter(end)
                || ChronoUnit.DAYS.between(start, end) > 365 || (model != null && model.length() > 255)
                || (userId != null && userId.length() > 128)) throw ApiException.badRequest("Invalid usage filters/page; maximum date span is 366 days");
        var result = repository.aggregate(start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant(),
            zone.getId(), model, userId, PageRequest.of(page, size));
        var content = result.getContent().stream().map(r -> new UsageGroup(r.getUsageDay(), r.getModel(), r.getUserId(),
            r.getRequestCount(), r.getTokensIn(), r.getTokensOut(), r.getUnknownTokenRequests(), r.getFailedRequests(),
            r.getUnknownCostRequests() == 0 ? r.getKnownCostUsd() : null, r.getKnownCostUsd(), r.getUnknownCostRequests())).toList();
        return new UsagePage(content, page, size, result.getTotalElements(), result.getTotalPages(), start, end, zone.getId());
    }
}
