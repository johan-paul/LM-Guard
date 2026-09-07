package com.lmguard.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response contract for the Python AI service.
 *
 * <p>Expected JSON:
 * <pre>
 * {
 *   "modelVersion": "paddleocr-2.7+vlm-1.2",
 *   "warnings": ["glare on lower panel"],
 *   "fields": [
 *     {"name":"MRP","value":"99","confidence":0.97,
 *      "boundingBox":{"x":64,"y":210,"width":150,"height":54}},
 *     {"name":"CONSUMER_CARE","value":null,"confidence":0.91}
 *   ]
 * }
 * </pre>
 *
 * <p>A field with {@code "value": null} means the service reports that declaration as absent,
 * and {@code confidence} is its confidence in the absence. Unknown properties are ignored so
 * the AI team can add fields without breaking this backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalyzeResponse(
        String modelVersion,
        List<String> warnings,
        List<AiField> fields
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiField(
            String name,
            String value,
            Double confidence,
            AiBoundingBox boundingBox,
            String rawText
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiBoundingBox(
            Integer x,
            Integer y,
            Integer width,
            Integer height
    ) {
    }
}
