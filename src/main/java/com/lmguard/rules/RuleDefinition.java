package com.lmguard.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lmguard.entity.enums.RuleType;
import com.lmguard.entity.enums.Severity;

import java.util.UUID;

/**
 * One machine-readable rule as the engine sees it.
 *
 * <p>Rules are data, not code. That is what makes them versionable, auditable, and amendable
 * by a domain expert without a rebuild - and what keeps the decision procedure inspectable
 * rather than buried in branching logic.
 *
 * @param id             database id, null for rules loaded from the bundled JSON file
 * @param ruleCode       stable identifier, e.g. {@code DEMO-RULE-001}
 * @param field          declaration this rule applies to
 * @param type           the deterministic check to perform
 * @param required       for REQUIRED_FIELD: whether absence is a breach
 * @param minConfidence  below this the outcome is INCONCLUSIVE, never NON_COMPLIANT
 * @param pattern        for PATTERN_MATCH: a Java regular expression
 * @param min            for NUMERIC_RANGE: inclusive lower bound
 * @param max            for NUMERIC_RANGE: inclusive upper bound
 * @param minLength      for MIN_LENGTH: minimum acceptable length
 * @param finding        plain-language statement recorded when the rule does not pass
 * @param remediation    suggested corrective action
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleDefinition(
        UUID id,
        String ruleCode,
        String ruleName,
        String description,
        String field,
        RuleType type,
        Boolean required,
        Double minConfidence,
        String pattern,
        Double min,
        Double max,
        Integer minLength,
        Severity severity,
        String finding,
        String remediation
) {

    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }

    public double minConfidenceOr(double fallback) {
        return minConfidence == null ? fallback : minConfidence;
    }

    public Severity severityOrDefault() {
        return severity == null ? Severity.MAJOR : severity;
    }

    public String findingOrDefault() {
        if (finding != null && !finding.isBlank()) {
            return finding;
        }
        return switch (type) {
            case REQUIRED_FIELD -> "Required declaration not detected: " + field;
            case PATTERN_MATCH -> "Declaration " + field + " is present but not in a valid format";
            case NUMERIC_RANGE -> "Declaration " + field + " is outside the permitted range";
            case MIN_LENGTH -> "Declaration " + field + " is too short to be valid";
        };
    }
}
