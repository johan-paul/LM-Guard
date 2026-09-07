package com.lmguard.dto.inspection;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "AssignmentHistoryResponse", description = "One assignment/reassignment audit-trail entry")
public record AssignmentHistoryResponse(
        UUID id,
        UUID fromInspectorId,
        String fromInspectorName,
        UUID toInspectorId,
        String toInspectorName,
        UUID fromZoneId,
        String fromZoneName,
        UUID toZoneId,
        String toZoneName,
        UUID assignedById,
        String assignedByName,
        String reason,
        Instant assignedAt
) {
}
