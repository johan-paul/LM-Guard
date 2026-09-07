package com.lmguard.risk;

import com.lmguard.config.properties.RiskProperties;
import com.lmguard.entity.enums.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Transparent weighted risk model.
 *
 * <p>Five factors, each contributing up to its configured weight:
 *
 * <pre>
 *   previous violations   up to +30   scales to full weight at 3 prior non-compliant inspections
 *   product changes       up to +20   scales to full weight at 2 recorded label changes
 *   online mismatch          +25      all-or-nothing
 *   category risk            +10      all-or-nothing, from the configured high-risk category list
 *   repeat issue             +15      all-or-nothing: the same rule failing again
 *                            ----
 *                     capped at 100
 * </pre>
 *
 * <p>Bands: {@code 0..lowMax} LOW, {@code ..mediumMax} MEDIUM, above that HIGH.
 * The counted factors scale rather than switching on at one, so a product with a single old
 * breach is not ranked alongside a repeat offender.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeightedRiskEngineService implements RiskEngineService {

    /** Prior non-compliant inspections needed for the factor to reach its full weight. */
    private static final double PREVIOUS_VIOLATIONS_SATURATION = 3.0;

    /** Recorded label changes needed for the factor to reach its full weight. */
    private static final double PRODUCT_CHANGES_SATURATION = 2.0;

    private final RiskProperties riskProperties;

    @Override
    public RiskAssessment assess(RiskInput input) {
        RiskProperties.Weights weights = riskProperties.weights();
        List<String> reasons = new ArrayList<>();

        int previousViolations = scaled(
                input.previousNonCompliantCount(), PREVIOUS_VIOLATIONS_SATURATION, weights.previousViolations());
        if (previousViolations > 0) {
            reasons.add("%d prior non-compliant inspection(s) (+%d)"
                    .formatted(input.previousNonCompliantCount(), previousViolations));
        }

        int productChanges = scaled(
                input.productChangeCount(), PRODUCT_CHANGES_SATURATION, weights.productChanges());
        if (productChanges > 0) {
            reasons.add("%d recorded change(s) to declared values (+%d)"
                    .formatted(input.productChangeCount(), productChanges));
        }

        int onlineMismatch = input.onlineMismatch() ? weights.onlineMismatch() : 0;
        if (onlineMismatch > 0) {
            reasons.add("declared values differ from the captured online listing (+%d)".formatted(onlineMismatch));
        }

        int categoryRisk = isHighRiskCategory(input.category()) ? weights.categoryRisk() : 0;
        if (categoryRisk > 0) {
            reasons.add("category '%s' is on the higher-risk list (+%d)".formatted(input.category(), categoryRisk));
        }

        int repeatIssue = input.hasRepeatIssue() ? weights.repeatIssue() : 0;
        if (repeatIssue > 0) {
            reasons.add("a rule breached previously on this product has been breached again (+%d)"
                    .formatted(repeatIssue));
        }

        int raw = previousViolations + productChanges + onlineMismatch + categoryRisk + repeatIssue;
        int total = Math.min(raw, riskProperties.maxScore());
        RiskLevel level = level(total);

        String explanation = reasons.isEmpty()
                ? "No risk factors present. Score 0 of %d (%s)."
                        .formatted(riskProperties.maxScore(), level)
                : "Score %d of %d (%s): %s.%s".formatted(
                        total, riskProperties.maxScore(), level, String.join("; ", reasons),
                        raw > total ? " Components summed to %d and were capped.".formatted(raw) : "");

        log.debug("Risk assessed: total={} level={} components=[prev={}, changes={}, online={}, category={}, repeat={}]",
                total, level, previousViolations, productChanges, onlineMismatch, categoryRisk, repeatIssue);

        return new RiskAssessment(previousViolations, productChanges, onlineMismatch,
                categoryRisk, repeatIssue, total, level, explanation);
    }

    /** Linear ramp to the full weight at {@code saturation} occurrences, then flat. */
    private int scaled(long count, double saturation, int weight) {
        if (count <= 0 || weight <= 0) {
            return 0;
        }
        double ratio = Math.min(1.0, count / saturation);
        return (int) Math.round(ratio * weight);
    }

    private RiskLevel level(int total) {
        RiskProperties.Thresholds thresholds = riskProperties.thresholds();
        if (total <= thresholds.lowMax()) {
            return RiskLevel.LOW;
        }
        if (total <= thresholds.mediumMax()) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }

    private boolean isHighRiskCategory(String category) {
        if (category == null || category.isBlank()) {
            return false;
        }
        Set<String> highRisk = riskProperties.highRiskCategorySet();
        return highRisk.contains(category.trim().toUpperCase(Locale.ROOT));
    }
}
