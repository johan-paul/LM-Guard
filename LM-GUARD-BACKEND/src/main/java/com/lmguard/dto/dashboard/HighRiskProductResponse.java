package com.lmguard.dto.dashboard;

import com.lmguard.entity.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "HighRiskProduct", description = "A product flagged for follow-up, with the score that flagged it")
public record HighRiskProductResponse(

        UUID productId,
        String productName,
        String brand,
        String category,

        @Schema(description = "The inspection that produced this score")
        UUID inspectionId,

        @Schema(example = "72") int riskScore,
        @Schema(example = "HIGH") RiskLevel riskLevel,

        @Schema(description = "Why the score is what it is")
        String explanation,

        Instant assessedAt,

        @Schema(description = "Count of this product's prior substantiated (non-compliant) inspections", example = "3")
        long previousViolations,

        @Schema(description = "Establishment the scoring inspection was carried out at - lets an admin "
                + "pre-fill a follow-up inspection for this product without looking it up separately",
                example = "SunFresh Retail Outlet")
        String establishment,

        @Schema(description = "Address of that establishment")
        String address
) {
}
