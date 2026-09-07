package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.FindingStatus;
import com.lmguard.entity.enums.Severity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FindingResponse(
        UUID id,
        int sequence,
        String ruleRef,
        String ruleName,
        Severity severity,
        String description,
        FindingStatus status,
        String note,
        List<UUID> evidenceIds,
        Instant createdAt
) {
}
