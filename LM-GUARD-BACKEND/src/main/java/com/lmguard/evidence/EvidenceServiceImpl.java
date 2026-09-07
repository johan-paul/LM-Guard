package com.lmguard.evidence;

import com.lmguard.ai.BoundingBox;
import com.lmguard.entity.Evidence;
import com.lmguard.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvidenceServiceImpl implements EvidenceService {

    private final EvidenceRepository evidenceRepository;

    @Override
    @Transactional
    public Evidence record(EvidenceRequest request) {
        BoundingBox box = request.box() == null ? BoundingBox.absent() : request.box();

        Evidence evidence = Evidence.builder()
                .violation(request.violation())
                .inspection(request.inspection())
                .imageUrl(request.imageUrl())
                .imagePath(request.imagePath())
                .x(box.x())
                .y(box.y())
                .width(box.width())
                .height(box.height())
                .ocrConfidence(toConfidence(request.ocrConfidence()))
                .description(request.description())
                .build();

        Evidence saved = evidenceRepository.save(evidence);
        log.debug("Recorded evidence {} for violation {} (region present: {})",
                saved.getId(), request.violation() == null ? null : request.violation().getId(), box.isComplete());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Evidence> forViolation(UUID violationId) {
        return evidenceRepository.findByViolationIdOrderByCreatedAtAsc(violationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Evidence> forInspection(UUID inspectionId) {
        return evidenceRepository.findByInspectionIdOrderByCreatedAtAsc(inspectionId);
    }

    /** Stored with 4 decimal places to match the NUMERIC(5,4) column. */
    private BigDecimal toConfidence(double value) {
        double clamped = Math.max(0, Math.min(1, value));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }
}
