package com.lmguard.dto.inspection;

import com.lmguard.dto.product.ProductResponse;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.InspectionType;
import com.lmguard.entity.enums.Priority;
import com.lmguard.entity.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full result of an inspection: the case details, the verdict, the facts it rests on, the
 * rules that failed, the evidence for each, and the risk assessment.
 *
 * <p>This is the shape the React client renders as the inspection detail screen.
 */
@Schema(name = "InspectionResult", description = "Complete inspection result")
public record InspectionResponse(

        @Schema(description = "Inspection identifier")
        UUID inspectionId,

        @Schema(description = "PENDING/IN_PROGRESS/PROCESSING before submission, else the inspector's final verdict",
                example = "IN_PROGRESS")
        InspectionStatus status,

        @Schema(description = "The rule engine's most recent suggestion - advisory only, never authoritative",
                example = "NON_COMPLIANT")
        InspectionStatus aiSuggestedStatus,

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

        @Schema(description = "Non-fatal problems from the most recent /analyze run worth "
                + "showing the inspector directly - e.g. the semantic (VLM) step being "
                + "unavailable, so free-text fields like manufacturer name could not be read "
                + "and the result fell back to OCR pattern matching only. Empty outside of a "
                + "fresh analyze() call; not persisted, since it describes that run, not the "
                + "inspection as a whole.")
        List<String> aiWarnings,

        @Schema(description = "URL of the package image under inspection")
        String imageUrl,

        @Schema(description = "The product inspected, if identified yet")
        ProductResponse product,

        @Schema(description = "Inspector currently assigned to this inspection")
        UUID inspectorId,

        @Schema(description = "Inspector name")
        String inspectorName,

        @Schema(description = "Zone this inspection belongs to, if assigned")
        UUID zoneId,

        @Schema(description = "Zone name")
        String zoneName,

        @Schema(description = "Establishment/premises being inspected")
        String establishment,

        @Schema(description = "Inspection address")
        String address,

        @Schema(description = "Why this inspection was opened")
        InspectionType inspectionType,

        @Schema(description = "Urgency")
        Priority priority,

        @Schema(description = "When this inspection is due")
        Instant dueDate,

        @Schema(description = "Inspector notes")
        String notes,

        @Schema(description = "Populated only when status is FAILED")
        String failureReason,

        @Schema(description = "Declarations observed on the package, with per-field verdicts")
        List<ExtractedFieldResponse> fields,

        @Schema(description = "Rules that did not pass (AI/rule-engine derived)")
        List<ViolationResponse> violations,

        @Schema(description = "Itemised risk assessment")
        RiskBreakdownResponse risk,

        Instant createdAt,

        @Schema(description = "When /analyze last completed - distinct from completedAt")
        Instant analyzedAt,

        @Schema(description = "When the inspector submitted their final decision")
        Instant completedAt
) {
}
