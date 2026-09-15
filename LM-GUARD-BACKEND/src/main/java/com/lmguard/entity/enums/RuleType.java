package com.lmguard.entity.enums;

/**
 * The deterministic check a rule performs. Adding a new type means adding a new
 * branch in the rule engine - never asking a model what the rule means.
 */
public enum RuleType {
    /** The declaration must be present and non-blank. */
    REQUIRED_FIELD,
    /** If present, the value must match a Java regular expression. */
    PATTERN_MATCH,
    /** If present, the leading numeric part must fall within [min, max]. */
    NUMERIC_RANGE,
    /** If present, the value must be at least {@code minLength} characters long. */
    MIN_LENGTH,
    /** If present, the leading numeric part must meet a minimum threshold selected from a
     * band table by ANOTHER fact's numeric value (e.g. Rule 7's minimum numeral height, whose
     * required mm depends on the declared net quantity's own weight/volume band) - see
     * {@link com.lmguard.rules.RuleDefinition#bandField()}/{@link com.lmguard.rules.RuleDefinition#bands()}. */
    NUMERIC_BAND
}
