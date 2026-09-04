package com.lmguard.risk;

import com.lmguard.entity.enums.RiskLevel;

/**
 * A scored risk assessment with every component preserved.
 *
 * <p>The components are the point. A single number an inspector cannot interrogate is not
 * usable as a basis for prioritising enforcement; a number that decomposes into five named
 * contributions is.
 */
public record RiskAssessment(
        int previousViolations,
        int productChanges,
        int onlineMismatch,
        int categoryRisk,
        int repeatIssue,
        int totalScore,
        RiskLevel riskLevel,
        String explanation
) {

    public static RiskAssessment zero() {
        return new RiskAssessment(0, 0, 0, 0, 0, 0, RiskLevel.LOW,
                "No risk factors present.");
    }
}
