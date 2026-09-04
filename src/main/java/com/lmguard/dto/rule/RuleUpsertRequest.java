package com.lmguard.dto.rule;

import com.lmguard.entity.enums.RuleType;
import com.lmguard.entity.enums.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "RuleUpsertRequest", description = "Create or amend a rule (ADMIN only)")
public record RuleUpsertRequest(

        @Schema(description = "Stable rule code", example = "DEMO-RULE-001")
        @NotBlank(message = "ruleCode is required")
        @Size(max = 50) String ruleCode,

        @Schema(description = "Short human-readable name", example = "Consumer care declaration present")
        @NotBlank(message = "ruleName is required")
        @Size(max = 255) String ruleName,

        @Schema(description = "What the rule checks and where it comes from")
        String description,

        @Schema(description = "Field the rule applies to", example = "CONSUMER_CARE")
        @NotBlank(message = "fieldName is required")
        @Size(max = 100) String fieldName,

        @Schema(description = "Deterministic check to perform", example = "REQUIRED_FIELD")
        @NotNull(message = "ruleType is required") RuleType ruleType,

        @Schema(description = "JSON parameters: pattern, minConfidence, min, max, minLength, finding, remediation",
                example = "{\"required\":true,\"minConfidence\":0.70,\"finding\":\"Required declaration not detected\"}")
        @NotBlank(message = "ruleDefinition is required") String ruleDefinition,

        @Schema(description = "Severity of a breach", example = "MAJOR")
        Severity severity,

        @Schema(description = "Ruleset version this rule belongs to", example = "DEMO-2026.1")
        @NotBlank(message = "version is required")
        @Size(max = 50) String version,

        @Schema(description = "Whether the rule participates in evaluation", example = "true")
        Boolean active
) {
}
