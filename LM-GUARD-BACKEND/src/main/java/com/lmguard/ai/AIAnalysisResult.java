package com.lmguard.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the AI layer returns for one image.
 *
 * <p>This type deliberately contains no compliance verdict. The AI says what it can see; the
 * deterministic rule engine says what that means legally.
 *
 * @param inspectionId  the inspection these facts belong to
 * @param facts         observations, one per declaration the AI looked for
 * @param provider      which implementation produced this: {@code MOCK} or {@code EXTERNAL}
 * @param modelVersion  version identifier reported by the AI service, when available
 * @param processingMs  wall-clock time the analysis took
 * @param warnings      non-fatal problems worth surfacing (blur, glare, partial crop)
 */
public record AIAnalysisResult(
        UUID inspectionId,
        List<ExtractedFact> facts,
        String provider,
        String modelVersion,
        long processingMs,
        List<String> warnings
) {

    public static final String PROVIDER_MOCK = "MOCK";
    public static final String PROVIDER_EXTERNAL = "EXTERNAL";

    public AIAnalysisResult {
        facts = facts == null ? List.of() : List.copyOf(facts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public Optional<ExtractedFact> fact(String fieldName) {
        return facts.stream()
                .filter(fact -> fact.fieldName().equalsIgnoreCase(fieldName))
                .findFirst();
    }

    /** Mean confidence across all observations, 0 when there are none. */
    public double meanConfidence() {
        if (facts.isEmpty()) {
            return 0;
        }
        return facts.stream().mapToDouble(ExtractedFact::confidence).average().orElse(0);
    }
}
