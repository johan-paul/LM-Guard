package com.lmguard.dto.inspection;

import com.lmguard.dto.product.ProductResponse;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full result of an inspection: the verdict, the facts it rests on, the rules that
 * failed, the evidence for each, and the risk assessment.
 *
 * <p>This is the shape the React client renders as the inspection detail screen.
 */
@Schema(name = "InspectionResult", description = "Complete inspection result")
public record InspectionResponse(

        @Schema(description = "Inspection identifier")
        UUID inspectionId,

        @Schema(description = "Verdict, or PENDING/PROCESSING before analysis completes", example = "NON_COMPLIANT")
        InspectionStatus status,

        @Schema(description = "Mean confidence of the observations this verdict rests on, 0..1", example = "0.94")
        BigDecimal overallConfidence,

        @Schema(description = "Risk score 0..100", example = "72")
        Integer riskScore,

        @Schema(description = "Risk band", example = "HIGH")
        RiskLevel riskLevel,

        @Schema(description = "Exact ruleset version this verdict was produced under", example = "DEMO-2026.1")
        String rulesetVersion,

        @Schema(description = "Which AI implementation produced the facts", example = "MOCK")
        String aiProvider,

        @Schema(description = "URL of the package image under inspection")
        String imageUrl,

        @Schema(description = "The product inspected")
        ProductResponse product,

        @Schema(description = "Inspector who opened this inspection")
        UUID inspectorId,

        @Schema(description = "Inspector name")
        String inspectorName,

        @Schema(description = "Inspector notes")
        String notes,

        @Schema(description = "Populated only when status is FAILED")
        String failureReason,

        @Schema(description = "Declarations observed on the package, with per-field verdicts")
        List<ExtractedFieldResponse> fields,

        @Schema(description = "Rules that did not pass")
        List<ViolationResponse> violations,

        @Schema(description = "Itemised risk assessment")
        RiskBreakdownResponse risk,

        Instant createdAt,
        Instant completedAt
) {
}
