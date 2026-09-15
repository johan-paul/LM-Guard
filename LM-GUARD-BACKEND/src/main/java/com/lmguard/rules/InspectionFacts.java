package com.lmguard.rules;

import com.lmguard.ai.AIAnalysisResult;
import com.lmguard.ai.BoundingBox;
import com.lmguard.ai.ExtractedFact;
import com.lmguard.entity.ExtractedField;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** Rebuilds facts from what is currently persisted for an inspection, rather than a fresh
     * {@link AIAnalysisResult} - used when re-evaluating after an inspector submits a
     * measurement (e.g. NUMERAL_HEIGHT_MM) outside the /analyze flow, without re-running the AI
     * vision pipeline. Confidence is stored as a scaled {@code BigDecimal}; {@code doubleValue()}
     * is exact enough here since it only ever feeds a threshold comparison, never persisted back. */
    public static InspectionFacts from(UUID inspectionId, List<ExtractedField> persisted) {
        Map<String, FactValue> facts = new LinkedHashMap<>();
        for (ExtractedField field : persisted) {
            double confidence = field.getConfidence() == null ? 0.0 : field.getConfidence().doubleValue();
            BoundingBox box = new BoundingBox(
                    field.getBoundingBoxX(), field.getBoundingBoxY(),
                    field.getBoundingBoxWidth(), field.getBoundingBoxHeight());
            facts.put(normalise(field.getFieldName()), new FactValue(field.getFieldValue(), confidence, box));
        }
        return new InspectionFacts(inspectionId, facts);
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
