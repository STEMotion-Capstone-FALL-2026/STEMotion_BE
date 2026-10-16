package com.stemotion.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/** Configured estimates, never provider billing or guessed prices. */
@Component
public class AiCostCalculator {
    public record Price(BigDecimal inputPerMillion, BigDecimal outputPerMillion, String version) {}
    public record Estimate(BigDecimal usd, Price price) {}
    private final Map<String, Price> prices = new HashMap<>();
    public AiCostCalculator(@Value("${AI_PRICING_JSON:{}}") String configured, ObjectMapper mapper) {
        try {
            var root = mapper.readTree(configured);
            if (!root.isObject()) throw new IllegalArgumentException();
            root.fields().forEachRemaining(entry -> {
                var value = entry.getValue();
                if (!value.path("inputPerMillion").isNumber() || !value.path("outputPerMillion").isNumber()
                        || !value.path("version").isTextual() || value.path("version").asText().isBlank()
                        || value.path("version").asText().length() > 128) throw new IllegalArgumentException();
                var input = value.path("inputPerMillion").decimalValue();
                var output = value.path("outputPerMillion").decimalValue();
                if (input.signum() < 0 || output.signum() < 0 || input.precision() > 18 || output.precision() > 18
                        || input.scale() > 10 || output.scale() > 10
                        || input.compareTo(new BigDecimal("10000000000")) >= 0 || output.compareTo(new BigDecimal("10000000000")) >= 0) throw new IllegalArgumentException();
                prices.put(entry.getKey(), new Price(input, output, value.path("version").asText()));
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI_PRICING_JSON must map exact model names to nonnegative rates and a version");
        }
    }
    public Estimate estimate(String model, Integer input, Integer output) {
        Price price = prices.get(model);
        if (price == null || input == null || output == null || input < 0 || output < 0) return null;
        var usd = price.inputPerMillion().multiply(BigDecimal.valueOf(input))
            .add(price.outputPerMillion().multiply(BigDecimal.valueOf(output)))
            .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
        if (usd.precision() - usd.scale() > 10) return null;
        return new Estimate(usd, price);
    }
}
