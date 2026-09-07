package com.lmguard.entity.enums;

/**
 * Why a rule did not pass. A violation row is only created for these two outcomes;
 * a passing rule produces no violation.
 */
public enum ViolationStatus {
    /** The rule was evaluated with sufficient confidence and the package does not satisfy it. */
    NON_COMPLIANT,
    /** The evidence was too weak to decide. Requires human review; never an enforcement basis. */
    INCONCLUSIVE
}
