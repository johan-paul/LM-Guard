package com.lmguard.risk;

/**
 * Scores how much attention a product deserves.
 *
 * <p>The MVP model is a transparent weighted sum, deliberately. A machine-learned score would
 * be harder to justify to the person acting on it and impossible to audit after the fact.
 * The weights live in configuration so a domain expert can retune them without a rebuild,
 * and every score carries the arithmetic that produced it.
 *
 * <p>Risk is a triage aid, never a verdict. It ranks what to look at first; it does not
 * decide compliance, and it has no bearing on the rule engine's output.
 */
public interface RiskEngineService {

    RiskAssessment assess(RiskInput input);
}
