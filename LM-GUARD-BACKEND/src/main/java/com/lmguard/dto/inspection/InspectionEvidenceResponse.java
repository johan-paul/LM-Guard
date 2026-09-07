package com.lmguard.dto.inspection;

import java.time.Instant;
import java.util.UUID;

public record InspectionEvidenceResponse(
        UUID id,
        UUID findingId,
        String imageUrl,
        String imagePath,
        String label,
        String description,
        Instant capturedAt
) {
}
