package com.lmguard.dto.rule;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "RuleSet", description = "All active rules for one ruleset version")
public record RuleSetResponse(

        @Schema(description = "Ruleset version identifier", example = "DEMO-2026.1")
        String version,

        @Schema(description = "Whether these rules are demo scaffolding or legally verified",
                example = "SAMPLE")
        String status,

        @Schema(description = "Mandatory notice about the provenance of these rules")
        String disclaimer,

        @Schema(description = "Number of active rules in this version", example = "9")
        int ruleCount,

        @Schema(description = "The rules")
        List<RuleResponse> rules
) {
}
