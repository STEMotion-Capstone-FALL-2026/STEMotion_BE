package com.stemotion.e2e;

import com.stemotion.domain.admin.service.AiUsageService;
import com.stemotion.domain.ai.entity.AiRequest;
import com.stemotion.domain.ai.repository.AiRequestRepository;
import com.stemotion.domain.ai.service.AiRequestLogService;
import com.stemotion.integration.client.AiGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/** Native PostgreSQL aggregate/projection/pagination test; fixture loopback only. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/postgres",
    "spring.datasource.username=stemotion_smoke","spring.datasource.password=",
    "spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.hikari.data-source-properties.stringtype=unspecified", "app.integration.ai.mode=embedded"})
@EnabledIfEnvironmentVariable(named="STEMOTION_LOCAL_E2E",matches="true")
@Transactional
class LocalAiUsageE2eTest {
    @Autowired AiRequestRepository requests;
    @Autowired AiUsageService usage;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.stemotion.domain.auth.repository.UserRepository users;
    @Autowired AiRequestLogService audit;
    @Autowired ObjectMapper mapper;
    void insert(String model, Integer in, Integer out, BigDecimal cost, String utc) {
        var row=requests.saveAndFlush(AiRequest.builder().requestType(AiRequest.RequestType.ANALYSIS)
            .model(model).inputTokens(in).outputTokens(out).monetaryCost(cost).costCurrency(cost==null?null:"USD")
            .callStatus(AiRequest.CallStatus.SUCCEEDED).build());
        jdbc.update("UPDATE ai_requests SET created_at=CAST(? AS timestamptz) WHERE CAST(id AS text)=?",utc,row.getId());
    }
    @Test void groupsRealUsageWithTimezoneAndNeverTurnsUnknownCostsIntoZero() {
        insert("fixture-a",10,20,new BigDecimal("0.01"),"2026-10-01T18:00:00Z");
        insert("fixture-a",null,null,null,"2026-10-01T19:00:00Z");
        insert("fixture-b",30,40,new BigDecimal("0.02"),"2026-10-01T20:00:00Z");
        var result=usage.find(LocalDate.of(2026,10,2),LocalDate.of(2026,10,2),null,null,0,1);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(2);
        var group=result.content().get(0);
        assertThat(group.day()).isEqualTo(LocalDate.of(2026,10,2));
        assertThat(group.model()).isEqualTo("fixture-a");
        assertThat(group.tokensIn()).isEqualTo(10);
        assertThat(group.tokensOut()).isEqualTo(20);
        assertThat(group.unknownTokenRequests()).isEqualTo(1);
        assertThat(group.estimatedCostUsd()).isNull();
        assertThat(group.knownEstimatedCostUsd()).isEqualByComparingTo("0.01");
        assertThat(group.unknownCostRequests()).isEqualTo(1);
        var filtered=usage.find(LocalDate.of(2026,10,2),LocalDate.of(2026,10,2),"fixture-b",null,0,20);
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.content().get(0).estimatedCostUsd()).isEqualByComparingTo("0.02");
        assertThat(usage.find(LocalDate.of(2026,10,1),LocalDate.of(2026,10,1),"fixture-a",null,0,20).content()).isEmpty();
    }
    @Test void rejectsUnboundedOrInvalidPaging() {
        assertThatThrownBy(()->usage.find(null,null,null,null,-1,20)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->usage.find(null,null,null,null,0,101)).isInstanceOf(RuntimeException.class);
    }

    @Test void separatesUsersAndAppliesUserFilter() {
        var first = users.saveAndFlush(com.stemotion.domain.auth.entity.User.builder().fullName("Fixture One")
            .email(java.util.UUID.randomUUID()+"@example.invalid").passwordHash("not-a-real-credential")
            .role(com.stemotion.domain.auth.entity.User.UserRole.WRITER).build());
        var second = users.saveAndFlush(com.stemotion.domain.auth.entity.User.builder().fullName("Fixture Two")
            .email(java.util.UUID.randomUUID()+"@example.invalid").passwordHash("not-a-real-credential")
            .role(com.stemotion.domain.auth.entity.User.UserRole.WRITER).build());
        for (var user:java.util.List.of(first,second)) {
            requests.saveAndFlush(AiRequest.builder().requester(user).requestType(AiRequest.RequestType.ANALYSIS)
                .model("fixture-user-model").inputTokens(10).outputTokens(20).callStatus(AiRequest.CallStatus.SUCCEEDED).build());
        }
        var rows=usage.find(null,null,"fixture-user-model",null,0,20);
        assertThat(rows.totalElements()).isEqualTo(2);
        var filtered=usage.find(null,null,"fixture-user-model",first.getId(),0,20);
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.content().get(0).userId()).isEqualTo(first.getId());
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void persistsCostAndPricingProvenanceThroughM01Transactions() throws Exception {
        String id=audit.start(AiRequest.RequestType.ANALYSIS,"fixture-priced","{}");
        try {
            audit.complete(id,new AiGateway.Generation(mapper.readTree("{}"),"fixture-priced",null,1000,2000,3000),15,null);
            var row=requests.findById(id).orElseThrow();
            assertThat(row.getMonetaryCost()).isEqualByComparingTo("0.0065");
            assertThat(row.getPricingVersion()).isEqualTo("local-test-v1");
            assertThat(row.getPricingInputPerMillion()).isEqualByComparingTo("1.5");
            assertThat(row.getPricingOutputPerMillion()).isEqualByComparingTo("2.5");
            assertThat(row.getCostCurrency()).isEqualTo("USD");
        } finally { requests.deleteById(id); }
    }
}
