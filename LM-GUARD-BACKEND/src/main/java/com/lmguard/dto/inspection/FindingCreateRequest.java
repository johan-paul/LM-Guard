package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record FindingCreateRequest(

        @Size(max = 100, message = "ruleRef must be at most 100 characters")
        String ruleRef,

        @Size(max = 255, message = "ruleName must be at most 255 characters")
        String ruleName,

        Severity severity,

        @NotBlank(message = "description is required")
        String description,

        String note,

        List<UUID> evidenceIds
) {
}
