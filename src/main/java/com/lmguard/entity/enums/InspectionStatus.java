package com.lmguard.entity.enums;

/**
 * Lifecycle and verdict of an inspection.
 *
 * <p>{@link #INCONCLUSIVE} is a deliberate first-class outcome: "the label could not be
 * read reliably" must never be silently reported as "the declaration is missing".
 * {@link #FAILED} is a pipeline error, not a compliance verdict.
 */
public enum InspectionStatus {
    PENDING,
    PROCESSING,
    COMPLIANT,
    NON_COMPLIANT,
    INCONCLUSIVE,
    FAILED;

    public boolean isTerminal() {
        return this != PENDING && this != PROCESSING;
    }

    public boolean isVerdict() {
        return this == COMPLIANT || this == NON_COMPLIANT || this == INCONCLUSIVE;
    }
}
