package com.lmguard.evidence;

import com.lmguard.entity.Evidence;

import java.util.List;
import java.util.UUID;

/**
 * Records the proof behind every finding.
 *
 * <p>Evidence is what separates an inspection platform from a classifier. A finding without a
 * region of an image, a confidence and a rule reference is an assertion; with them it is
 * something an inspector can verify and, if necessary, defend. Every violation gets an
 * evidence record - including findings where the declaration was absent and there is no
 * region to point at, because "we looked here and found nothing" is itself evidence.
 */
public interface EvidenceService {

    /** Creates and persists one evidence record. */
    Evidence record(EvidenceRequest request);

    List<Evidence> forViolation(UUID violationId);

    List<Evidence> forInspection(UUID inspectionId);
}
