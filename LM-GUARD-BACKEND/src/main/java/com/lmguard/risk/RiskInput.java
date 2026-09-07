package com.lmguard.risk;

import java.util.Set;

/**
 * The facts the risk model scores. Assembled by the inspection service from history; the
 * risk engine itself performs no queries, which keeps it trivially testable.
 *
 * @param previousNonCompliantCount how many earlier inspections of this product were non-compliant
 * @param productChangeCount        how many times the declared facts have changed
 * @param onlineMismatch            whether a captured online listing disagrees with the package
 * @param category                  product category, matched against the high-risk list
 * @param currentRuleCodes          rule codes breached in this inspection
 * @param previousRuleCodes         rule codes breached in earlier inspections of this product
 */
public record RiskInput(
        long previousNonCompliantCount,
        long productChangeCount,
        boolean onlineMismatch,
        String category,
        Set<String> currentRuleCodes,
        Set<String> previousRuleCodes
) {

    public RiskInput {
        currentRuleCodes = currentRuleCodes == null ? Set.of() : Set.copyOf(currentRuleCodes);
        previousRuleCodes = previousRuleCodes == null ? Set.of() : Set.copyOf(previousRuleCodes);
    }

    /** A rule that has failed on this product before and is failing again. */
    public boolean hasRepeatIssue() {
        return currentRuleCodes.stream().anyMatch(previousRuleCodes::contains);
    }

    public static RiskInput empty() {
        return new RiskInput(0, 0, false, null, Set.of(), Set.of());
    }
}
