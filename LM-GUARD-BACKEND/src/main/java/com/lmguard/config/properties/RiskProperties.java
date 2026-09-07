package com.lmguard.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Weights and bands for the transparent risk model.
 *
 * <p>These are configuration, not code, so a domain expert can retune the model without a
 * rebuild - and so the numbers behind any score can be produced on demand.
 */
@ConfigurationProperties(prefix = "lmguard.risk")
public record RiskProperties(
        Weights weights,
        Thresholds thresholds,
        int maxScore,
        String highRiskCategories
) {

    public RiskProperties {
        weights = weights == null ? new Weights(30, 20, 25, 10, 15) : weights;
        thresholds = thresholds == null ? new Thresholds(30, 60) : thresholds;
        if (maxScore <= 0) {
            maxScore = 100;
        }
        highRiskCategories = highRiskCategories == null ? "" : highRiskCategories;
    }

    /** Points each factor contributes at full strength. */
    public record Weights(
            int previousViolations,
            int productChanges,
            int onlineMismatch,
            int categoryRisk,
            int repeatIssue
    ) {
    }

    /** Upper bound of each band. Anything above {@code mediumMax} is HIGH. */
    public record Thresholds(
            int lowMax,
            int mediumMax
    ) {
    }

    public Set<String> highRiskCategorySet() {
        return Arrays.stream(highRiskCategories.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> part.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
