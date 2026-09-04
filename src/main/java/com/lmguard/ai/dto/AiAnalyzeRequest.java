package com.lmguard.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Request body sent to the Python AI service.
 *
 * <p>This is the contract the AI team implements. It stays small on purpose: the image is
 * passed by URL rather than by value, so a large upload never travels through this backend
 * a second time.
 */
public record AiAnalyzeRequest(
        String imageUrl,
        UUID inspectionId,
        List<String> requestedFields
) {
}
