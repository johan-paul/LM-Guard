package com.lmguard.dto.product;

import com.lmguard.dto.inspection.InspectionSummaryResponse;
import com.lmguard.entity.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Aggregated inspection history for one product - what the six-step workflow's Product History
 * step shows an inspector before they record their own findings.
 */
@Schema(name = "ProductInspectionSummary", description = "A product's inspection and violation history")
public record ProductInspectionSummaryResponse(

        @Schema(description = "Completed inspections of this product, any verdict")
        int previousInspections,

        int compliantCount,
        int nonCompliantCount,
        int inconclusiveCount,

        @Schema(description = "Total NON_COMPLIANT violations raised across every past inspection of this product")
        int previousViolations,

        @Schema(description = "Rule codes breached in more than one separate inspection of this product")
        int repeatViolations,

        @Schema(description = "Risk band of the most recent scored inspection, if any")
        RiskLevel latestRiskLevel,

        @Schema(description = "The completed inspections themselves, newest first")
        List<InspectionSummaryResponse> recentInspections
) {
}
