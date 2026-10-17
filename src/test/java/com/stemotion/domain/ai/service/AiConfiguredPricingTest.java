package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class AiConfiguredPricingTest {
    @Test void configuredCurrentRatesCalculateMockTokensWithoutAnyApi() throws Exception {
        var pricing = Files.readString(Path.of("config/ai-pricing-2026-10-17.json"));
        var calculator = new AiCostCalculator(pricing, new ObjectMapper());
        assertThat(calculator.estimate("gpt-4.1", 1000, 500).usd())
            .isEqualByComparingTo("0.006");
        assertThat(calculator.estimate("gpt-4o-mini", 1000, 500).usd())
            .isEqualByComparingTo("0.00045");
        assertThat(calculator.estimate("gpt-4.1-2025-04-14", 1000, 500).usd()).isEqualByComparingTo("0.006");
        assertThat(calculator.estimate("gpt-4o-mini-2024-07-18", 1000, 500).usd()).isEqualByComparingTo("0.00045");
        assertThat(calculator.estimate("gpt-4o", 1000, 500)).isNull();
        assertThat(calculator.estimate("gpt-4.1", null, 500)).isNull();
    }

    @Test void missingPricingEnvKeepsEstimatesUnknown() {
        new ApplicationContextRunner().withBean(ObjectMapper.class)
            .withUserConfiguration(AiCostCalculator.class)
            .run(context -> assertThat(context.getBean(AiCostCalculator.class)
                .estimate("gpt-4.1", 1000, 500)).isNull());
    }
}
