package com.lmguard.entity.enums;

/**
 * What an inspector has done about a violation, independent of {@link ViolationStatus} - the
 * rule engine's verdict on whether the observation itself was correct never changes, but the
 * case built on top of it moves through a normal review workflow.
 */
public enum ViolationCaseStatus {
    /** Freshly raised by analysis; no inspector action yet. */
    OPEN,
    /** An inspector has requested a fresh capture rather than deciding on the current evidence. */
    UNDER_REVIEW,
    /** An inspector confirmed the finding as a genuine breach. */
    CONFIRMED,
    /** An inspector reviewed the finding and found it compliant after all. */
    DISMISSED,
    /** Escalated to the zonal controller for enforcement review. */
    ESCALATED
}
