package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.InspectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "InspectionSubmitRequest", description = "The inspector's final, independent decision")
public record InspectionSubmitRequest(

        @NotNull(message = "finalDecision is required")
        @Schema(description = "Must be COMPLIANT, NON_COMPLIANT or INCONCLUSIVE - never PENDING/IN_PROGRESS/PROCESSING/FAILED",
                example = "NON_COMPLIANT")
        InspectionStatus finalDecision,

        @Schema(description = "Officer notes, appended to the inspection record")
        String notes
) {
}
