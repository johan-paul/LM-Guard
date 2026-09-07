package com.lmguard.dto.violation;

import com.lmguard.entity.enums.ViolationCaseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "ViolationDecisionRequest", description = "Record an inspector decision on a violation case")
public record ViolationDecisionRequest(

        @NotNull(message = "caseStatus is required")
        @Schema(description = "The case-management state to move this violation to", example = "CONFIRMED")
        ViolationCaseStatus caseStatus,

        @Schema(description = "Free-text note, kept on the case")
        String note
) {
}
