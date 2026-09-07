package com.lmguard.mapper;

import com.lmguard.dto.inspection.FindingResponse;
import com.lmguard.entity.Finding;
import com.lmguard.entity.InspectionEvidence;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FindingMapper {

    public FindingResponse toResponse(Finding finding, List<InspectionEvidence> linkedEvidence) {
        if (finding == null) {
            return null;
        }
        return new FindingResponse(
                finding.getId(),
                finding.getSequence(),
                finding.getRuleRef(),
                finding.getRuleName(),
                finding.getSeverity(),
                finding.getDescription(),
                finding.getStatus(),
                finding.getNote(),
                linkedEvidence.stream().map(InspectionEvidence::getId).toList(),
                finding.getCreatedAt());
    }
}
