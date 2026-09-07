package com.lmguard.dto.inspection;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Proof behind a violation: the image, and the region of it that produced the finding. */
@Schema(name = "Evidence", description = "Image-backed proof for a violation")
public record EvidenceResponse(

        UUID id,

        @Schema(description = "Violation this evidence supports")
        UUID violationId,

        @Schema(description = "Inspection this evidence belongs to")
        UUID inspectionId,

        @Schema(description = "URL of the package image")
        String imageUrl,

        @Schema(example = "120") Integer x,
        @Schema(example = "340") Integer y,
        @Schema(example = "200") Integer width,
        @Schema(example = "80") Integer height,

        @Schema(description = "OCR confidence for the region, 0..1", example = "0.91")
        BigDecimal ocrConfidence,

        @Schema(description = "What this evidence shows, in plain language")
        String description,

        Instant createdAt
) {
}
