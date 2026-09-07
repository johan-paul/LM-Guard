package com.lmguard.rules;

import com.lmguard.ai.BoundingBox;
import com.lmguard.config.properties.RulesProperties;
import com.lmguard.entity.enums.ComplianceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The deterministic rule engine. This class, and only this class, decides compliance.
 *
 * <p>Evaluation of a single rule follows one procedure, and the confidence threshold is the
 * heart of it:
 *
 * <ol>
 *   <li><b>Declaration absent, rule requires it.</b> If the AI is confident about the absence
 *       (confidence at or above the rule's threshold), that is a NON_COMPLIANT finding. If it
 *       is not confident, the finding is INCONCLUSIVE - a blurred photograph is not evidence
 *       that a declaration is missing.</li>
 *   <li><b>Declaration absent, rule does not require it.</b> Passes.</li>
 *   <li><b>Declaration present but read below the threshold.</b> INCONCLUSIVE. The engine will
 *       not judge a value it cannot trust it read correctly.</li>
 *   <li><b>Declaration present and read confidently.</b> The type-specific check runs, and the
 *       result is COMPLIANT or NON_COMPLIANT.</li>
 * </ol>
 *
 * <p>Outcomes roll up worst-first: any NON_COMPLIANT makes the inspection NON_COMPLIANT; failing
 * that, any INCONCLUSIVE makes it INCONCLUSIVE; otherwise COMPLIANT.
 *
 * <p>Every branch above is arithmetic and string matching. No model is consulted, nothing is
 * inferred, and the same facts always produce the same verdict.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeterministicRuleEngineService implements RuleEngineService {

    private final RuleCatalog ruleCatalog;
    private final RulesProperties rulesProperties;

    /** Compiled regexes are reused; a malformed pattern is reported once, not on every inspection. */
    private final ConcurrentHashMap<String, Optional<Pattern>> patternCache = new ConcurrentHashMap<>();

    @Override
    public ComplianceResult evaluate(InspectionFacts facts, String rulesetVersion) {
        RuleSet ruleSet = ruleCatalog.load(rulesetVersion);
        String version = ruleSet.rulesetVersion() == null ? rulesetVersion : ruleSet.rulesetVersion();

        List<RuleFinding> findings = new ArrayList<>(ruleSet.rules().size());
        ComplianceStatus overall = ComplianceStatus.COMPLIANT;

        for (RuleDefinition rule : ruleSet.rules()) {
            RuleFinding finding = evaluateRule(rule, facts);
            findings.add(finding);
            overall = ComplianceStatus.worst(overall, finding.status());
        }

        double confidence = facts.meanConfidence();
        log.debug("Inspection {} evaluated against ruleset {}: {} ({} rules, {} breaches)",
                facts.inspectionId(), version, overall, findings.size(),
                findings.stream().filter(RuleFinding::isBreach).count());

        return new ComplianceResult(overall, version, findings, confidence);
    }

    @Override
    public RuleSet ruleSet(String rulesetVersion) {
        return ruleCatalog.load(rulesetVersion);
    }

    // ------------------------------------------------------------------
    // Single-rule evaluation
    // ------------------------------------------------------------------

    private RuleFinding evaluateRule(RuleDefinition rule, InspectionFacts facts) {
        double threshold = rule.minConfidenceOr(rulesProperties.defaultMinConfidence());
        Optional<FactValue> maybeFact = facts.get(rule.field());

        // The AI did not report on this declaration at all. That is not evidence of absence.
        if (maybeFact.isEmpty()) {
            if (!rule.isRequired() && rule.type() != com.lmguard.entity.enums.RuleType.REQUIRED_FIELD) {
                return pass(rule, null, 1.0, BoundingBox.absent());
            }
            return finding(rule, ComplianceStatus.INCONCLUSIVE,
                    "Declaration '" + rule.field() + "' was not assessed by the analysis service, so its "
                            + "presence could not be determined. Manual verification required.",
                    0.0, null, BoundingBox.absent());
        }

        FactValue fact = maybeFact.get();

        if (!fact.isPresent()) {
            return evaluateAbsence(rule, fact, threshold);
        }

        // Present, but read too poorly to judge.
        if (fact.confidence() < threshold) {
            return finding(rule, ComplianceStatus.INCONCLUSIVE,
                    "Declaration '" + rule.field() + "' was detected but read with low confidence ("
                            + formatConfidence(fact.confidence()) + " below the required "
                            + formatConfidence(threshold) + "). Manual verification required.",
                    fact.confidence(), fact.trimmed(), fact.box());
        }

        return applyCheck(rule, fact);
    }

    /** The declaration is reported as absent. Whether that is a breach depends on confidence. */
    private RuleFinding evaluateAbsence(RuleDefinition rule, FactValue fact, double threshold) {
        boolean mandatory = rule.isRequired()
                || rule.type() == com.lmguard.entity.enums.RuleType.REQUIRED_FIELD;

        if (!mandatory) {
            return pass(rule, null, fact.confidence(), fact.box());
        }

        if (fact.confidence() >= threshold) {
            return finding(rule, ComplianceStatus.NON_COMPLIANT, rule.findingOrDefault(),
                    fact.confidence(), null, fact.box());
        }

        return finding(rule, ComplianceStatus.INCONCLUSIVE,
                "Declaration '" + rule.field() + "' was not detected, but confidence in that reading was low ("
                        + formatConfidence(fact.confidence()) + " below the required "
                        + formatConfidence(threshold) + "). Manual verification required.",
                fact.confidence(), null, fact.box());
    }

    /** The type-specific check, run only on a value read confidently enough to judge. */
    private RuleFinding applyCheck(RuleDefinition rule, FactValue fact) {
        String value = fact.trimmed();

        return switch (rule.type()) {
            case REQUIRED_FIELD ->
                // Presence was the whole requirement, and the value is present.
                    pass(rule, value, fact.confidence(), fact.box());

            case PATTERN_MATCH -> {
                Optional<Pattern> pattern = compile(rule);
                if (pattern.isEmpty()) {
                    // A rule we cannot apply must never silently pass as compliant.
                    yield finding(rule, ComplianceStatus.INCONCLUSIVE,
                            "Rule " + rule.ruleCode() + " could not be applied: its pattern is not a valid "
                                    + "regular expression. Manual verification required.",
                            fact.confidence(), value, fact.box());
                }
                yield pattern.get().matcher(value).matches()
                        ? pass(rule, value, fact.confidence(), fact.box())
                        : finding(rule, ComplianceStatus.NON_COMPLIANT, rule.findingOrDefault(),
                                fact.confidence(), value, fact.box());
            }

            case MIN_LENGTH -> {
                int minLength = rule.minLength() == null ? 1 : rule.minLength();
                yield value.length() >= minLength
                        ? pass(rule, value, fact.confidence(), fact.box())
                        : finding(rule, ComplianceStatus.NON_COMPLIANT, rule.findingOrDefault(),
                                fact.confidence(), value, fact.box());
            }

            case NUMERIC_RANGE -> {
                Optional<Double> number = leadingNumber(value);
                if (number.isEmpty()) {
                    yield finding(rule, ComplianceStatus.NON_COMPLIANT,
                            "Declaration '" + rule.field() + "' does not contain a readable number.",
                            fact.confidence(), value, fact.box());
                }
                double parsed = number.get();
                boolean belowMin = rule.min() != null && parsed < rule.min();
                boolean aboveMax = rule.max() != null && parsed > rule.max();
                yield (belowMin || aboveMax)
                        ? finding(rule, ComplianceStatus.NON_COMPLIANT, rule.findingOrDefault(),
                                fact.confidence(), value, fact.box())
                        : pass(rule, value, fact.confidence(), fact.box());
            }
        };
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RuleFinding pass(RuleDefinition rule, String value, double confidence, BoundingBox box) {
        return new RuleFinding(rule.id(), rule.ruleCode(), rule.field(), ComplianceStatus.COMPLIANT,
                rule.severityOrDefault(), null, null, confidence, value, box);
    }

    private RuleFinding finding(RuleDefinition rule, ComplianceStatus status, String message,
                                double confidence, String value, BoundingBox box) {
        return new RuleFinding(rule.id(), rule.ruleCode(), rule.field(), status,
                rule.severityOrDefault(), message, rule.remediation(), confidence, value, box);
    }

    private Optional<Pattern> compile(RuleDefinition rule) {
        if (rule.pattern() == null || rule.pattern().isBlank()) {
            return Optional.empty();
        }
        return patternCache.computeIfAbsent(rule.pattern(), raw -> {
            try {
                return Optional.of(Pattern.compile(raw));
            } catch (PatternSyntaxException ex) {
                log.error("Rule {} has an invalid regular expression and will be reported as "
                        + "INCONCLUSIVE until it is fixed: {}", rule.ruleCode(), ex.getMessage());
                return Optional.empty();
            }
        });
    }

    /** Pulls the first number out of a value like "500 g" or "Rs. 99.00". */
    private Optional<Double> leadingNumber(String value) {
        java.util.regex.Matcher matcher = Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(matcher.group(1).replace(',', '.')));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String formatConfidence(double confidence) {
        // Three places, not two: confidence is compared at full precision (fact.confidence() <
        // threshold) but was being *displayed* at two decimal places, so a value like 0.6963
        // against a 0.70 threshold rendered as the nonsensical "0.70 below the required 0.70" -
        // same rounded text on both sides of a comparison that was in fact not a tie.
        return String.format(java.util.Locale.ROOT, "%.3f", confidence);
    }
}
