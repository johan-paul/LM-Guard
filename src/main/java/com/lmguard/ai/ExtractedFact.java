package com.lmguard.ai;

/**
 * One observation from the AI/OCR layer.
 *
 * <p>The distinction that matters throughout LM-GUARD: a {@code null} {@code value} means the
 * AI reports the declaration as <em>absent</em>, and {@code confidence} is then its confidence
 * in that absence. Low confidence with a null value means "could not tell", which the rule
 * engine must treat as INCONCLUSIVE rather than as a violation.
 *
 * @param fieldName   canonical field name, e.g. {@code NET_QUANTITY}
 * @param value       observed text, or null when not detected
 * @param confidence  0..1
 * @param boundingBox where on the image it was read; may be an empty box for an absence
 * @param rawText     unprocessed OCR text this fact was derived from, when available
 */
public record ExtractedFact(
        String fieldName,
        String value,
        double confidence,
        BoundingBox boundingBox,
        String rawText
) {

    public ExtractedFact {
        if (confidence < 0) {
            confidence = 0;
        }
        if (confidence > 1) {
            confidence = 1;
        }
        if (boundingBox == null) {
            boundingBox = BoundingBox.absent();
        }
    }

    public static ExtractedFact detected(String fieldName, String value, double confidence, BoundingBox box) {
        return new ExtractedFact(fieldName, value, confidence, box, value);
    }

    public static ExtractedFact notDetected(String fieldName, double confidence) {
        return new ExtractedFact(fieldName, null, confidence, BoundingBox.absent(), null);
    }

    public boolean isPresent() {
        return value != null && !value.isBlank();
    }
}
