package com.lmguard.dto.product;

import com.lmguard.entity.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ProductSummary", description = "A product with its compliance profile, for the registry list")
public record ProductSummaryResponse(
        UUID id,
        String productName,
        String brand,
        String category,
        String barcode,
        Instant createdAt,
        Instant updatedAt,

        @Schema(description = "Most recently computed composite risk score, if any inspection has been scored")
        Integer riskScore,
        RiskLevel riskLevel,

        @Schema(description = "Substantiated violations across every inspection of this product")
        long violationCount,

        @Schema(description = "Violations still awaiting a case decision (OPEN/UNDER_REVIEW/ESCALATED)")
        long openViolationCount,

        @Schema(description = "When this product was last inspected, if ever")
        Instant lastInspectionAt,

        @Schema(description = "The most recently captured package photo for this product, if any")
        String imageUrl
) {
}
