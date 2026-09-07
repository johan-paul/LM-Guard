package com.lmguard.dto.inspection;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "InspectionNotesRequest", description = "Save the inspector's working notes for an in-progress inspection")
public record InspectionNotesRequest(

        @Schema(description = "Free-text notes, replacing whatever was previously saved")
        String notes
) {
}
