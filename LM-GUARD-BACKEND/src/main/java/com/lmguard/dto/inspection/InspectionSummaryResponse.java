package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.InspectionType;
import com.lmguard.entity.enums.Priority;
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
        InspectionStatus aiSuggestedStatus,
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
        UUID zoneId,
        String zoneName,
        String establishment,
        InspectionType inspectionType,
        Priority priority,
        Instant dueDate,
        String imageUrl,
        Instant createdAt,
        Instant completedAt
) {
}
