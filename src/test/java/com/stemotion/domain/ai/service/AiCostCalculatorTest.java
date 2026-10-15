package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stemotion.domain.ai.entity.AiRequest;
import com.stemotion.domain.ai.repository.AiRequestRepository;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.integration.client.AiGateway;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCostCalculatorTest {
    final ObjectMapper mapper = new ObjectMapper();
    final String rates = """
        {"test-model":{"inputPerMillion":1.5,"outputPerMillion":2.5,"version":"fixture-v1"}}
        """;
    @Test void estimatesOnlyKnownActualModelAndCompleteTokenCounts() {
        var cost = new AiCostCalculator(rates, mapper);
        assertThat(cost.estimate("test-model", 1000, 2000).usd()).isEqualByComparingTo("0.0065");
        assertThat(cost.estimate("test-model", 0, 0).usd()).isZero();
        assertThat(cost.estimate("unknown", 1000, 2000)).isNull();
        assertThat(cost.estimate("test-model", null, 20)).isNull();
        assertThat(cost.estimate("test-model", -1, 20)).isNull();
    }
    @Test void invalidPricesFailConfigurationInsteadOfGuessing() {
        assertThatThrownBy(() -> new AiCostCalculator("{\"m\":{\"inputPerMillion\":-1,\"outputPerMillion\":2,\"version\":\"v\"}}", mapper))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiCostCalculator("[]", mapper)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void auditStampsRateVersionAndUnknownCostsRemainNull() throws Exception {
        var repo=mock(AiRequestRepository.class);
        var row=AiRequest.builder().build();
        when(repo.findById("id")).thenReturn(Optional.of(row));
        var service=new AiRequestLogService(repo,mock(UserRepository.class),new AiCostCalculator(rates,mapper));
        service.complete("id",new AiGateway.Generation(mapper.readTree("{}"),"test-model",null,1000,2000,3000),25,null);
        assertThat(row.getModel()).isEqualTo("test-model");
        assertThat(row.getCostTokens()).isEqualTo(3000);
        assertThat(row.getMonetaryCost()).isEqualByComparingTo("0.0065");
        assertThat(row.getPricingVersion()).isEqualTo("fixture-v1");
        assertThat(row.getCostCurrency()).isEqualTo("USD");
        service.complete("id",new AiGateway.Generation(mapper.readTree("{}"),"unknown"),30,null);
        assertThat(row.getMonetaryCost()).isNull();
        assertThat(row.getCostCurrency()).isNull();
        assertThat(row.getPricingVersion()).isNull();
    }
}
