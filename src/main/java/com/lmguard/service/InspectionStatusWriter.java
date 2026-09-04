package com.lmguard.service;

import com.lmguard.entity.Inspection;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.repository.InspectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes inspection status transitions in their own short transaction.
 *
 * <p>This exists for one reason: when the analysis pipeline throws, its transaction rolls
 * back. Recording {@code FAILED} inside that same transaction would be undone along with
 * everything else, leaving an inspection stuck in {@code PROCESSING} forever with no trace of
 * what went wrong. Writing the status from a separate bean, after the pipeline transaction
 * has already completed, makes the failure durable.
 *
 * <p>{@code REQUIRES_NEW} is deliberate, and it is also why these methods are called only
 * from outside an active pipeline transaction - a nested transaction contending for a row
 * the outer one has already locked would deadlock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InspectionStatusWriter {

    private static final int MAX_FAILURE_REASON_LENGTH = 2000;

    private final InspectionRepository inspectionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(UUID inspectionId) {
        inspectionRepository.findById(inspectionId).ifPresent(inspection -> {
            inspection.setStatus(InspectionStatus.PROCESSING);
            inspection.setFailureReason(null);
            inspectionRepository.save(inspection);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID inspectionId, String reason) {
        inspectionRepository.findById(inspectionId).ifPresentOrElse(inspection -> {
            inspection.setStatus(InspectionStatus.FAILED);
            inspection.setFailureReason(truncate(reason));
            inspection.setCompletedAt(Instant.now());
            inspectionRepository.save(inspection);
            log.warn("Inspection {} marked FAILED: {}", inspectionId, reason);
        }, () -> log.warn("Could not mark inspection {} as FAILED: it no longer exists", inspectionId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Inspection reload(UUID inspectionId) {
        return inspectionRepository.findDetailedById(inspectionId).orElse(null);
    }

    private String truncate(String reason) {
        if (reason == null) {
            return "Analysis failed with no further detail.";
        }
        return reason.length() <= MAX_FAILURE_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
