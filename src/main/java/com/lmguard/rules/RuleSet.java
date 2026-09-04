package com.lmguard.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A versioned collection of rules, exactly as loaded from JSON or from the database.
 *
 * <p>{@code disclaimer} is carried through deliberately: any ruleset that is not legally
 * verified must say so wherever it appears, including in API responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleSet(
        String rulesetVersion,
        String status,
        String disclaimer,
        List<String> notes,
        List<RuleDefinition> rules
) {

    public static final String STATUS_SAMPLE = "SAMPLE";

    public RuleSet {
        rules = rules == null ? List.of() : List.copyOf(rules);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public boolean isSample() {
        return STATUS_SAMPLE.equalsIgnoreCase(status);
    }
}
