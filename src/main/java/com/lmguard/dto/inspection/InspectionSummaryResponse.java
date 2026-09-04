package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Compact inspection shape for list and dashboard views. */
@Schema(name = "InspectionSummary", description = "Inspection as shown in lists")
public record InspectionSummaryResponse(
        UUID inspectionId,
        InspectionStatus status,
        BigDecimal overallConfidence,
        Integer riskScore,
        RiskLevel riskLevel,
        String rulesetVersion,
        UUID productId,
        String productName,
        String brand,
        String category,
        UUID inspectorId,
        String inspectorName,
        String imageUrl,
        Instant createdAt,
        Instant completedAt
) {
}
