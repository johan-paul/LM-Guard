package com.lmguard.dto.rule;

import com.lmguard.entity.enums.RuleType;
import com.lmguard.entity.enums.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "Rule", description = "A versioned deterministic rule")
public record RuleResponse(
        UUID id,
        String ruleCode,
        String ruleName,
        String description,
        String fieldName,
        RuleType ruleType,
        String ruleDefinition,
        Severity severity,
        String version,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
