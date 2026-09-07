package com.lmguard.mapper;

import com.lmguard.dto.inspection.InspectionEvidenceResponse;
import com.lmguard.entity.InspectionEvidence;
import org.springframework.stereotype.Component;

@Component
public class InspectionEvidenceMapper {

    public InspectionEvidenceResponse toResponse(InspectionEvidence item) {
        if (item == null) {
            return null;
        }
        return new InspectionEvidenceResponse(
                item.getId(),
                item.getFinding() == null ? null : item.getFinding().getId(),
                item.getImageUrl(),
                item.getImagePath(),
                item.getLabel(),
                item.getDescription(),
                item.getCapturedAt(),
                item.getCapturedBy() == null ? null : item.getCapturedBy().getId(),
                item.getCapturedBy() == null ? null : item.getCapturedBy().getName());
    }
}
