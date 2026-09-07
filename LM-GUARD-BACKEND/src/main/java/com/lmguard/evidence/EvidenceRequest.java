package com.lmguard.evidence;

import com.lmguard.ai.BoundingBox;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.Violation;

/**
 * Everything needed to record one piece of evidence.
 *
 * @param violation   the finding this evidence supports
 * @param inspection  the inspection it belongs to
 * @param imageUrl    URL of the package image
 * @param imagePath   bucket-relative storage path of that image
 * @param box         the region that produced the finding; may be empty for an absence
 * @param ocrConfidence confidence of the reading behind the finding, 0..1
 * @param description what this evidence shows, in plain language
 */
public record EvidenceRequest(
        Violation violation,
        Inspection inspection,
        String imageUrl,
        String imagePath,
        BoundingBox box,
        double ocrConfidence,
        String description
) {
}
