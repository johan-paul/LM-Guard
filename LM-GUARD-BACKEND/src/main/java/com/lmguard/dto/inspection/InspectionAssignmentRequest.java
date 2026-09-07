package com.lmguard.dto.inspection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(name = "InspectionAssignmentRequest", description = "Assign or reassign an inspection to an inspector")
public record InspectionAssignmentRequest(

        @NotNull(message = "inspectorId is required")
        @Schema(description = "User id (not the officer code) of the inspector to assign this inspection to")
        UUID inspectorId,

        @Schema(description = "Zone to move the inspection to. Omit to keep the current zone.")
        UUID zoneId,

        @Schema(description = "Free-text reason, kept in the audit trail")
        String reason
) {
}
