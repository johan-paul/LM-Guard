package com.lmguard.mapper;

import com.lmguard.dto.inspector.InspectorInspectionSummary;
import com.lmguard.dto.inspector.InspectorResponse;
import com.lmguard.entity.InspectorProfile;
import com.lmguard.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InspectorMapper {

    public InspectorResponse toResponse(InspectorProfile profile,
                                        long activeAssignments,
                                        long completedThisMonth,
                                        long recordsFiled,
                                        long openRecords,
                                        String temporaryPassword,
                                        List<InspectorInspectionSummary> inspections) {
        User user = profile.getUser();
        String fullName = (profile.getFullName() == null || profile.getFullName().isBlank())
                ? user.getName()
                : profile.getFullName();

        return new InspectorResponse(
                profile.getOfficerCode(),
                user.getId(),
                user.getName(),
                fullName,
                profile.getRank(),
                user.getEmail(),
                profile.getPhone(),
                profile.getZone() == null ? null : profile.getZone().getName(),
                user.isEnabled() ? "ACTIVE" : "INACTIVE",
                activeAssignments,
                completedThisMonth,
                recordsFiled,
                openRecords,
                profile.getLastActiveAt(),
                profile.getJoinedOn(),
                temporaryPassword,
                inspections
        );
    }
}
