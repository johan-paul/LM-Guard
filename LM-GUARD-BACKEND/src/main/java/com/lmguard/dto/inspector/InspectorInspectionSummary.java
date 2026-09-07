package com.lmguard.dto.inspector;

import com.lmguard.entity.enums.InspectionStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of an inspector's recent-inspections list, shown in the Inspector detail dialog. */
public record InspectorInspectionSummary(
        UUID id,
        String productName,
        InspectionStatus status,
        Integer riskScore,
        Instant date
) {
}
