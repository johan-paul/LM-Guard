package com.lmguard.dto.violation;

import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.Severity;
import com.lmguard.entity.enums.ViolationCaseStatus;
import com.lmguard.entity.enums.ViolationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "ViolationSummary", description = "One row of the case queue")
public record ViolationSummaryResponse(

        UUID id,
        UUID inspectionId,
        UUID productId,
        String productName,
        String manufacturer,

        @Schema(description = "Plain-language statement of what was found")
        String finding,

        @Schema(description = "The declaration field this rule applies to - the closest thing to a "
                + "\"violation type\" this data model tracks", example = "CONSUMER_CARE")
        String fieldName,

        String ruleCode,
        Severity severity,

        @Schema(description = "Confidence that this finding is correct, 0..1")
        BigDecimal confidence,

        @Schema(description = "Risk band of the inspection this violation was raised on")
        RiskLevel riskLevel,

        @Schema(description = "The rule engine's verdict - never changes after analysis")
        ViolationStatus status,

        @Schema(description = "Inspector case-management state")
        ViolationCaseStatus caseStatus,

        Instant detectedAt
) {
}
