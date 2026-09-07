package com.lmguard.mapper;

import com.lmguard.dto.inspection.ChecklistEntryResponse;
import com.lmguard.entity.ChecklistEntry;
import org.springframework.stereotype.Component;

@Component
public class ChecklistMapper {

    public ChecklistEntryResponse toResponse(ChecklistEntry entry) {
        if (entry == null) {
            return null;
        }
        return new ChecklistEntryResponse(
                entry.getId(), entry.getItemCode(), entry.getTitle(), entry.getGuidance(),
                entry.getResult(), entry.getNote(), entry.getUpdatedAt());
    }
}
