package com.lmguard.dto.inspector;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An inspecting officer as the admin console's Inspector Management screen renders it.
 *
 * <p>{@code id} is the human-readable badge number (e.g. "LM-INS-101"), matching what the
 * console has always shown as the officer identifier - {@code userId} carries the real
 * backend primary key for anything that needs it (assignment, for instance).
 */
@Schema(name = "InspectorResponse", description = "An inspecting officer directory record")
public record InspectorResponse(
        String id,
        UUID userId,
        String name,
        String fullName,
        String rank,
        String email,
        String phone,
        String zone,
        String status,
        long activeAssignments,
        long completedThisMonth,
        long recordsFiled,
        long openRecords,
        Instant lastActive,
        LocalDate joinedOn,

        @Schema(description = "Present only in the response to POST /api/inspectors - shown once so the "
                + "admin can hand it to the officer. Never returned again afterwards.")
        String temporaryPassword,

        @Schema(description = "Present only on GET /api/inspectors/{id}")
        List<InspectorInspectionSummary> inspections
) {
    /** List/roster views omit the per-record detail this constructor doesn't need to compute. */
    public InspectorResponse withoutDetail() {
        return new InspectorResponse(id, userId, name, fullName, rank, email, phone, zone, status,
                activeAssignments, completedThisMonth, recordsFiled, openRecords, lastActive, joinedOn,
                temporaryPassword, null);
    }
}
