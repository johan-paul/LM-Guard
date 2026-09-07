package com.lmguard.service;

import com.lmguard.dto.inspection.FindingCreateRequest;
import com.lmguard.dto.inspection.FindingResponse;
import com.lmguard.entity.Finding;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.InspectionEvidence;
import com.lmguard.exception.ApiException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.mapper.FindingMapper;
import com.lmguard.repository.FindingRepository;
import com.lmguard.repository.InspectionEvidenceRepository;
import com.lmguard.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Inspector-authored findings from the six-step workflow - distinct from AI/rule-engine-derived
 * {@link com.lmguard.entity.Violation}s. An inspector records these regardless of whether the
 * AI/rule engine flagged the same issue.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FindingService {

    private final FindingRepository findingRepository;
    private final InspectionEvidenceRepository inspectionEvidenceRepository;
    private final InspectionService inspectionService;
    private final FindingMapper findingMapper;

    @Transactional(readOnly = true)
    public List<FindingResponse> list(UUID inspectionId) {
        inspectionService.requireInspection(inspectionId);
        List<InspectionEvidence> evidence = inspectionEvidenceRepository.findByInspectionIdOrderByCapturedAtAsc(inspectionId);
        return findingRepository.findByInspectionIdOrderBySequenceAsc(inspectionId).stream()
                .map(finding -> findingMapper.toResponse(finding, evidenceFor(finding.getId(), evidence)))
                .toList();
    }

    @Transactional
    public FindingResponse create(UUID inspectionId, FindingCreateRequest request, UUID callerId) {
        Inspection inspection = inspectionService.requireInspection(inspectionId);
        requireAssignedInspectorOrAdmin(inspection, callerId);

        int nextSequence = (int) findingRepository.countByInspection_Id(inspectionId) + 1;
        Finding finding = findingRepository.save(Finding.builder()
                .inspection(inspection)
                .sequence(nextSequence)
                .ruleRef(request.ruleRef())
                .ruleName(request.ruleName())
                .severity(request.severity() == null ? com.lmguard.entity.enums.Severity.MAJOR : request.severity())
                .description(request.description())
                .note(request.note())
                .build());

        List<InspectionEvidence> linked = linkEvidence(finding, request.evidenceIds());

        log.info("Finding #{} recorded on inspection {} by {}", finding.getSequence(), inspectionId, callerId);
        return findingMapper.toResponse(finding, linked);
    }

    private List<InspectionEvidence> linkEvidence(Finding finding, List<UUID> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return List.of();
        }
        List<InspectionEvidence> items = inspectionEvidenceRepository.findAllById(evidenceIds);
        items.forEach(item -> item.setFinding(finding));
        return inspectionEvidenceRepository.saveAll(items);
    }

    private List<InspectionEvidence> evidenceFor(UUID findingId, List<InspectionEvidence> all) {
        return all.stream().filter(item -> item.getFinding() != null && item.getFinding().getId().equals(findingId)).toList();
    }

    private void requireAssignedInspectorOrAdmin(Inspection inspection, UUID callerId) {
        boolean isAssignedInspector = inspection.getInspector() != null
                && inspection.getInspector().getId().equals(callerId);
        if (!isAssignedInspector && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the assigned inspector can add findings");
        }
    }
}
