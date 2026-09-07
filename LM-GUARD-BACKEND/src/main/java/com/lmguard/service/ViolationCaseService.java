package com.lmguard.service;

import com.lmguard.dto.violation.ViolationDecisionRequest;
import com.lmguard.dto.violation.ViolationDetailResponse;
import com.lmguard.dto.violation.ViolationSummaryResponse;
import com.lmguard.entity.User;
import com.lmguard.entity.Violation;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.ViolationCaseStatus;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.ViolationCaseMapper;
import com.lmguard.repository.UserRepository;
import com.lmguard.repository.ViolationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Violations screen's case queue: every substantiated or inconclusive rule breach, as a
 * reviewable case an inspector moves through OPEN -&gt; (UNDER_REVIEW | CONFIRMED | DISMISSED |
 * ESCALATED). This is a separate concern from {@link FindingService}, which records an
 * inspector's own field-app findings during the six-step workflow - a violation here is what the
 * rule engine raised automatically during analysis.
 */
@Service
@RequiredArgsConstructor
public class ViolationCaseService {

    private final ViolationRepository violationRepository;
    private final UserRepository userRepository;
    private final ViolationCaseMapper mapper;

    @Transactional(readOnly = true)
    public Page<ViolationSummaryResponse> search(ViolationCaseStatus caseStatus, RiskLevel riskLevel,
                                                  Instant since, Pageable pageable) {
        return violationRepository.searchCases(caseStatus, riskLevel, since, pageable).map(mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public ViolationDetailResponse detail(UUID id) {
        Violation violation = requireDetailed(id);
        UUID productId = violation.getInspection() == null || violation.getInspection().getProduct() == null
                ? null : violation.getInspection().getProduct().getId();
        List<Violation> relatedCases = productId == null
                ? List.of()
                : violationRepository.findOtherForProduct(productId, id);
        return mapper.toDetail(violation, relatedCases);
    }

    @Transactional
    public ViolationDetailResponse decide(UUID id, ViolationDecisionRequest request, UUID decidedByUserId) {
        Violation violation = requireDetailed(id);

        violation.setCaseStatus(request.caseStatus());
        violation.setDecidedAt(Instant.now());
        violation.setDecisionNote(request.note());
        if (decidedByUserId != null) {
            User decidedBy = userRepository.findById(decidedByUserId).orElse(null);
            violation.setDecidedBy(decidedBy);
        }
        violationRepository.save(violation);

        UUID productId = violation.getInspection() == null || violation.getInspection().getProduct() == null
                ? null : violation.getInspection().getProduct().getId();
        List<Violation> relatedCases = productId == null
                ? List.of()
                : violationRepository.findOtherForProduct(productId, id);
        return mapper.toDetail(violation, relatedCases);
    }

    private Violation requireDetailed(UUID id) {
        return violationRepository.findDetailedById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.VIOLATION_NOT_FOUND, id));
    }
}
