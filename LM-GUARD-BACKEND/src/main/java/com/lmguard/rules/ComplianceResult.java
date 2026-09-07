package com.lmguard.rules;

import com.lmguard.entity.enums.ComplianceStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The verdict for one inspection, with the per-rule findings that produced it.
 *
 * @param status          overall outcome; worst individual outcome wins
 * @param rulesetVersion  the exact ruleset this verdict was produced under
 * @param findings        every rule evaluated, passes included, so the decision is auditable
 * @param overallConfidence mean confidence of the observations the verdict rests on
 */
public record ComplianceResult(
        ComplianceStatus status,
        String rulesetVersion,
        List<RuleFinding> findings,
        double overallConfidence
) {

    public ComplianceResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /** Only the rules that did not pass - these become violation records. */
    public List<RuleFinding> breaches() {
        return findings.stream().filter(RuleFinding::isBreach).toList();
    }

    /** Worst outcome per field, used to colour the field list in the UI. */
    public Map<String, ComplianceStatus> statusByField() {
        Map<String, ComplianceStatus> statuses = new LinkedHashMap<>();
        for (RuleFinding finding : findings) {
            statuses.merge(finding.fieldName(), finding.status(), ComplianceStatus::worst);
        }
        return statuses;
    }

    public long countOf(ComplianceStatus target) {
        return findings.stream().filter(finding -> finding.status() == target).count();
    }
}
