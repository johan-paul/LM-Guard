package com.lmguard.mapper;

import com.lmguard.dto.inspection.EvidenceResponse;
import com.lmguard.dto.violation.ViolationDetailResponse;
import com.lmguard.dto.violation.ViolationSummaryResponse;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps a {@link Violation} to the case-queue shapes the Violations screen renders - distinct
 * from {@link ViolationMapper}, which produces the narrower {@code ViolationResponse} embedded
 * inside an inspection result.
 */
@Component
@RequiredArgsConstructor
public class ViolationCaseMapper {

    private final EvidenceMapper evidenceMapper;

    public ViolationSummaryResponse toSummary(Violation v) {
        Inspection inspection = v.getInspection();
        return new ViolationSummaryResponse(
                v.getId(),
                inspection == null ? null : inspection.getId(),
                inspection == null || inspection.getProduct() == null ? null : inspection.getProduct().getId(),
                inspection == null || inspection.getProduct() == null ? null : inspection.getProduct().getProductName(),
                inspection == null || inspection.getProduct() == null ? null : inspection.getProduct().getBrand(),
                v.getFinding(),
                v.getFieldName(),
                v.getRuleCode(),
                v.getSeverity(),
                v.getDecisionConfidence(),
                inspection == null ? null : inspection.getRiskLevel(),
                v.getStatus(),
                v.getCaseStatus(),
                v.getCreatedAt());
    }

    public ViolationDetailResponse toDetail(Violation v, List<Violation> relatedCases) {
        Inspection inspection = v.getInspection();
        List<EvidenceResponse> evidence = v.getEvidence() == null
                ? List.of()
                : v.getEvidence().stream().map(evidenceMapper::toResponse).toList();

        return new ViolationDetailResponse(
                v.getId(),
                inspection == null ? null : inspection.getId(),
                inspection == null || inspection.getProduct() == null ? null : inspection.getProduct().getId(),
                inspection == null || inspection.getProduct() == null ? null : inspection.getProduct().getProductName(),
                inspection == null || inspection.getProduct() == null ? null : inspection.getProduct().getBrand(),
                v.getFinding(),
                v.getRemediation(),
                v.getFieldName(),
                v.getRuleCode(),
                v.getRule() == null ? null : v.getRule().getRuleName(),
                v.getRule() == null ? null : v.getRule().getDescription(),
                v.getRule() == null ? null : v.getRule().getRuleDefinition(),
                v.getSeverity(),
                v.getDecisionConfidence(),
                v.getObservedValue(),
                inspection == null ? null : inspection.getRiskLevel(),
                inspection == null ? null : inspection.getRulesetVersion(),
                inspection == null || inspection.getZone() == null ? null : inspection.getZone().getName(),
                inspection == null || inspection.getInspector() == null ? null : inspection.getInspector().getName(),
                v.getStatus(),
                v.getCaseStatus(),
                v.getDecidedBy() == null ? null : v.getDecidedBy().getName(),
                v.getDecidedAt(),
                v.getDecisionNote(),
                v.getCreatedAt(),
                evidence,
                relatedCases.stream()
                        .map(rc -> new ViolationDetailResponse.RelatedCase(
                                rc.getId(), rc.getFinding(), rc.getCaseStatus(), rc.getCreatedAt()))
                        .toList());
    }
}
