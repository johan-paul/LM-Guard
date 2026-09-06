package com.lmguard.mapper;

import com.lmguard.dto.inspection.EvidenceResponse;
import com.lmguard.dto.inspection.ViolationResponse;
import com.lmguard.entity.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ViolationMapper {

    private final EvidenceMapper evidenceMapper;

    public ViolationResponse toResponse(Violation violation) {
        if (violation == null) {
            return null;
        }
        List<EvidenceResponse> allEvidence = violation.getEvidence() == null
                ? List.of()
                : violation.getEvidence().stream().map(evidenceMapper::toResponse).toList();

        // The frontend highlights one region per violation; the rest stay available in allEvidence.
        EvidenceResponse primary = allEvidence.isEmpty() ? null : allEvidence.get(0);

        return new ViolationResponse(
                violation.getId(),
                violation.getRuleCode(),
                violation.getFieldName(),
                violation.getFinding(),
                violation.getRemediation(),
                violation.getStatus(),
                violation.getSeverity(),
                violation.getDecisionConfidence(),
                violation.getObservedValue(),
                primary,
                allEvidence
        );
    }
}
