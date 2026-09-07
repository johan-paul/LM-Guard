package com.lmguard.dto.violation;

import com.lmguard.dto.inspection.EvidenceResponse;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.Severity;
import com.lmguard.entity.enums.ViolationCaseStatus;
import com.lmguard.entity.enums.ViolationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "ViolationDetail", description = "The full case for one violation")
public record ViolationDetailResponse(

        UUID id,
        UUID inspectionId,
        UUID productId,
        String productName,
        String manufacturer,

        String finding,
        String remediation,
        String fieldName,
        String ruleCode,

        @Schema(description = "Present only when the rule that produced this finding is still on record")
        String ruleName,
        @Schema(description = "The rule's own description, if it is still on record")
        String ruleDescription,
        @Schema(description = "The rule's raw evaluation parameters, if it is still on record")
        String ruleDefinition,

        Severity severity,
        BigDecimal confidence,
        String observedValue,

        RiskLevel riskLevel,
        String rulesetVersion,
        String zone,
        String inspector,

        ViolationStatus status,
        ViolationCaseStatus caseStatus,
        String decidedBy,
        Instant decidedAt,
        String decisionNote,

        Instant detectedAt,

        List<EvidenceResponse> evidence,

        @Schema(description = "Other violations on record for the same product")
        List<RelatedCase> relatedCases
) {
    @Schema(name = "ViolationRelatedCase")
    public record RelatedCase(UUID id, String finding, ViolationCaseStatus caseStatus, Instant detectedAt) {
    }
}
