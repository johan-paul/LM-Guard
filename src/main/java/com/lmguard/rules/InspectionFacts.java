package com.lmguard.rules;

import com.lmguard.ai.AIAnalysisResult;
import com.lmguard.ai.ExtractedFact;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The complete set of observations one inspection is judged on.
 *
 * <p>This is the only input the rule engine accepts. It carries no image, no model, and no
 * hint of a verdict - which is what makes rule evaluation a pure function of the facts and
 * the ruleset, and therefore reproducible and explainable after the fact.
 */
public record InspectionFacts(UUID inspectionId, Map<String, FactValue> facts) {

    public InspectionFacts {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    public static InspectionFacts from(AIAnalysisResult result) {
        Map<String, FactValue> facts = new LinkedHashMap<>();
        for (ExtractedFact fact : result.facts()) {
            facts.put(normalise(fact.fieldName()),
                    new FactValue(fact.value(), fact.confidence(), fact.boundingBox()));
        }
        return new InspectionFacts(result.inspectionId(), facts);
    }

    public Optional<FactValue> get(String fieldName) {
        return Optional.ofNullable(facts.get(normalise(fieldName)));
    }

    public boolean isEmpty() {
        return facts.isEmpty();
    }

    /** Mean confidence across every observation, 0 when there are none. */
    public double meanConfidence() {
        if (facts.isEmpty()) {
            return 0;
        }
        return facts.values().stream().mapToDouble(FactValue::confidence).average().orElse(0);
    }

    private static String normalise(String fieldName) {
        return fieldName == null ? "" : fieldName.trim().toUpperCase(Locale.ROOT);
    }
}
