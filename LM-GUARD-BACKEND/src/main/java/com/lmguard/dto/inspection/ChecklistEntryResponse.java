package com.lmguard.dto.inspection;

import com.lmguard.entity.enums.CheckResult;

import java.time.Instant;
import java.util.UUID;

public record ChecklistEntryResponse(
        UUID id,
        String itemCode,
        String title,
        String guidance,
        CheckResult result,
        String note,
        Instant updatedAt
) {
}
