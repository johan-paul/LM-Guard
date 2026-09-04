package com.lmguard.rules;

import com.lmguard.ai.BoundingBox;
import com.lmguard.entity.enums.ComplianceStatus;
import com.lmguard.entity.enums.Severity;

import java.util.UUID;

/**
 * The outcome of evaluating one rule against one set of facts.
 *
 * <p>Carries everything needed to write a violation and its evidence, so nothing has to be
 * recomputed - or re-guessed - further down the pipeline.
 *
 * @param decisionConfidence confidence that this outcome is correct, 0..1
 * @param box                the image region behind the finding; empty for an absence
 */
public record RuleFinding(
        UUID ruleId,
        String ruleCode,
        String fieldName,
        ComplianceStatus status,
        Severity severity,
        String finding,
        String remediation,
        double decisionConfidence,
        String observedValue,
        BoundingBox box
) {

    public boolean isPass() {
        return status == ComplianceStatus.COMPLIANT;
    }

    public boolean isBreach() {
        return status != ComplianceStatus.COMPLIANT;
    }
}
