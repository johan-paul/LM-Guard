package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RiskBreakdown", description = "Itemised risk score. Every component is shown so the total can be explained.")
public record RiskBreakdownResponse(

        @Schema(description = "Points from prior non-compliant inspections of this product", example = "30")
        int previousViolations,

        @Schema(description = "Points from changes to the product's declared facts over time", example = "20")
        int productChanges,

        @Schema(description = "Points from a mismatch against a captured online listing", example = "0")
        int onlineMismatch,

        @Schema(description = "Points from the product's category risk", example = "10")
        int categoryRisk,

        @Schema(description = "Points from the same rule failing again on this product", example = "15")
        int repeatIssue,

        @Schema(description = "Sum of the components, capped at 100", example = "72")
        int totalScore,

        @Schema(description = "Band derived from the total", example = "HIGH")
        RiskLevel riskLevel,

        @Schema(description = "Human-readable explanation of how the total was reached")
        String explanation
) {
}
