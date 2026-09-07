package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.Severity;
import com.lmguard.entity.enums.ViolationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(name = "Violation", description = "A rule that did not pass, with its supporting evidence")
public record ViolationResponse(

        UUID id,

        @Schema(description = "Code of the rule that produced this finding", example = "DEMO-RULE-001")
        String ruleCode,

        @Schema(description = "Field the rule applies to", example = "CONSUMER_CARE")
        String fieldName,

        @Schema(description = "What was found, in plain language",
                example = "Required declaration not detected")
        String finding,

        @Schema(description = "Suggested corrective action")
        String remediation,

        @Schema(description = "NON_COMPLIANT (decided) or INCONCLUSIVE (needs human review)",
                example = "NON_COMPLIANT")
        ViolationStatus status,

        @Schema(description = "How serious this breach is", example = "MAJOR")
        Severity severity,

        @Schema(description = "Confidence that this finding is correct, 0..1", example = "0.91")
        BigDecimal decisionConfidence,

        @Schema(description = "The value observed, if any")
        String observedValue,

        @Schema(description = "Primary evidence for this violation; null when there is no image region to point at")
        EvidenceResponse evidence,

        @Schema(description = "All evidence records for this violation")
        List<EvidenceResponse> allEvidence
) {
}
