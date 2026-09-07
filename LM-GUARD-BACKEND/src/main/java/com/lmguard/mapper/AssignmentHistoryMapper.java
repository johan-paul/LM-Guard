package com.lmguard.mapper;

import com.lmguard.dto.inspection.AssignmentHistoryResponse;
import com.lmguard.entity.AssignmentHistory;
import org.springframework.stereotype.Component;

@Component
public class AssignmentHistoryMapper {

    public AssignmentHistoryResponse toResponse(AssignmentHistory history) {
        if (history == null) {
            return null;
        }
        return new AssignmentHistoryResponse(
                history.getId(),
                history.getFromInspector() == null ? null : history.getFromInspector().getId(),
                history.getFromInspector() == null ? null : history.getFromInspector().getName(),
                history.getToInspector().getId(),
                history.getToInspector().getName(),
                history.getFromZone() == null ? null : history.getFromZone().getId(),
                history.getFromZone() == null ? null : history.getFromZone().getName(),
                history.getToZone() == null ? null : history.getToZone().getId(),
                history.getToZone() == null ? null : history.getToZone().getName(),
                history.getAssignedBy().getId(),
                history.getAssignedBy().getName(),
                history.getReason(),
                history.getCreatedAt()
        );
    }
}
