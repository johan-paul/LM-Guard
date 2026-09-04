package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.ComplianceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One declaration the AI reported, with the rule engine's verdict for that field.
 *
 * <p>A null {@code value} with a high {@code confidence} means "the AI is confident this
 * declaration is not on the package", which is a very different statement from a low
 * confidence reading - and the {@code status} reflects that difference.
 */
@Schema(name = "ExtractedField", description = "A declaration observed on the package, with its field-level verdict")
public record ExtractedFieldResponse(

        UUID id,

        @Schema(description = "Canonical field name", example = "NET_QUANTITY")
        String name,

        @Schema(description = "Observed value; null when the declaration was not detected", example = "500 g")
        String value,

        @Schema(description = "AI confidence in this observation, 0..1", example = "0.95")
        BigDecimal confidence,

        @Schema(description = "Verdict for this field, decided by the deterministic rule engine",
                example = "COMPLIANT")
        ComplianceStatus status,

        @Schema(description = "Where on the image this was read; null for an absent declaration")
        BoundingBoxResponse boundingBox
) {
}
