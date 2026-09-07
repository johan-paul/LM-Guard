package com.lmguard.entity.enums;

/** Outcome of evaluating one rule, one field, or a whole inspection. */
public enum ComplianceStatus {
    COMPLIANT,
    NON_COMPLIANT,
    INCONCLUSIVE;

    /**
     * Combines two outcomes, worst-wins: any NON_COMPLIANT dominates, then INCONCLUSIVE.
     * Used to roll rule outcomes up to field level and field outcomes up to inspection level.
     */
    public static ComplianceStatus worst(ComplianceStatus a, ComplianceStatus b) {
        if (a == NON_COMPLIANT || b == NON_COMPLIANT) {
            return NON_COMPLIANT;
        }
        if (a == INCONCLUSIVE || b == INCONCLUSIVE) {
            return INCONCLUSIVE;
        }
        return COMPLIANT;
    }

    public InspectionStatus toInspectionStatus() {
        return switch (this) {
            case COMPLIANT -> InspectionStatus.COMPLIANT;
            case NON_COMPLIANT -> InspectionStatus.NON_COMPLIANT;
            case INCONCLUSIVE -> InspectionStatus.INCONCLUSIVE;
        };
    }
}
