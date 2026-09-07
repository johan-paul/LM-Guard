package com.lmguard.rules;

/**
 * Decides compliance. Deterministically, and alone.
 *
 * <p>This interface is the legal boundary of the system. Implementations must be pure
 * functions of {@code (facts, rulesetVersion)}: the same inputs always yield the same
 * verdict, and nothing else - no model, no network call, no clock - may influence the
 * outcome. That property is what allows a decision made months ago to be reproduced and
 * defended today.
 *
 * <p>No implementation may consult a language model. The AI layer's role ends at producing
 * facts; interpreting the law from those facts happens here, in code a person can read.
 */
public interface RuleEngineService {

    /**
     * @param facts          observations from the AI layer
     * @param rulesetVersion which version of the rules to judge against
     * @return the verdict and every individual rule outcome behind it
     * @throws com.lmguard.exception.RuleEngineException when the ruleset is missing or malformed
     */
    ComplianceResult evaluate(InspectionFacts facts, String rulesetVersion);

    /** Loads a ruleset without evaluating it - used by the rules API and by the seeder. */
    RuleSet ruleSet(String rulesetVersion);
}
