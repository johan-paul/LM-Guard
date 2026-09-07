package com.lmguard.service;

import com.lmguard.dto.inspection.ChecklistBulkUpsertRequest;
import com.lmguard.dto.inspection.ChecklistEntryRequest;
import com.lmguard.dto.inspection.ChecklistEntryResponse;
import com.lmguard.entity.ChecklistEntry;
import com.lmguard.entity.Inspection;
import com.lmguard.exception.ApiException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.mapper.ChecklistMapper;
import com.lmguard.repository.ChecklistEntryRepository;
import com.lmguard.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The inspector's own answers to the six-step workflow's compliance checklist.
 *
 * <p>The checklist's fixed item set (code/title/guidance) is owned by the Flutter app; this
 * service only persists the inspector's {@code result}/{@code note} for each item, upserted by
 * item code so re-saving the same checklist never creates duplicates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChecklistService {

    private final ChecklistEntryRepository checklistEntryRepository;
    private final InspectionService inspectionService;
    private final ChecklistMapper checklistMapper;

    @Transactional(readOnly = true)
    public List<ChecklistEntryResponse> get(UUID inspectionId) {
        inspectionService.requireInspection(inspectionId);
        return checklistEntryRepository.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
                .map(checklistMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<ChecklistEntryResponse> upsert(UUID inspectionId, ChecklistBulkUpsertRequest request, UUID callerId) {
        Inspection inspection = inspectionService.requireInspection(inspectionId);
        requireAssignedInspectorOrAdmin(inspection, callerId);

        List<ChecklistEntry> saved = request.items().stream()
                .map(item -> upsertOne(inspection, item))
                .map(checklistEntryRepository::save)
                .toList();

        log.info("Checklist for inspection {} saved ({} items) by {}", inspectionId, saved.size(), callerId);
        return saved.stream().map(checklistMapper::toResponse).toList();
    }

    private ChecklistEntry upsertOne(Inspection inspection, ChecklistEntryRequest item) {
        ChecklistEntry entry = checklistEntryRepository
                .findByInspection_IdAndItemCode(inspection.getId(), item.itemCode())
                .orElseGet(() -> ChecklistEntry.builder().inspection(inspection).itemCode(item.itemCode()).build());

        entry.setTitle(item.title());
        entry.setGuidance(item.guidance());
        entry.setResult(item.result());
        entry.setNote(item.note());
        return entry;
    }

    private void requireAssignedInspectorOrAdmin(Inspection inspection, UUID callerId) {
        boolean isAssignedInspector = inspection.getInspector() != null
                && inspection.getInspector().getId().equals(callerId);
        if (!isAssignedInspector && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the assigned inspector can update this checklist");
        }
    }
}
