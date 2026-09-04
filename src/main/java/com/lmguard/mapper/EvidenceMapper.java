package com.lmguard.mapper;

import com.lmguard.dto.inspection.EvidenceResponse;
import com.lmguard.entity.Evidence;
import org.springframework.stereotype.Component;

@Component
public class EvidenceMapper {

    public EvidenceResponse toResponse(Evidence evidence) {
        if (evidence == null) {
            return null;
        }
        return new EvidenceResponse(
                evidence.getId(),
                evidence.getViolation() == null ? null : evidence.getViolation().getId(),
                evidence.getInspection() == null ? null : evidence.getInspection().getId(),
                evidence.getImageUrl(),
                evidence.getX(),
                evidence.getY(),
                evidence.getWidth(),
                evidence.getHeight(),
                evidence.getOcrConfidence(),
                evidence.getDescription(),
                evidence.getCreatedAt()
        );
    }
}
