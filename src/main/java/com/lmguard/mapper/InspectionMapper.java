package com.lmguard.mapper;

import com.lmguard.dto.inspection.BoundingBoxResponse;
import com.lmguard.dto.inspection.ExtractedFieldResponse;
import com.lmguard.dto.inspection.InspectionResponse;
import com.lmguard.dto.inspection.InspectionSummaryResponse;
import com.lmguard.dto.inspection.RiskBreakdownResponse;
import com.lmguard.dto.inspection.ViolationResponse;
import com.lmguard.entity.ExtractedField;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.RiskScore;
import com.lmguard.entity.Violation;
import com.lmguard.entity.enums.ComplianceStatus;
import com.lmguard.entity.enums.ViolationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InspectionMapper {

    private final ProductMapper productMapper;
    private final ViolationMapper violationMapper;

    /**
     * Builds the full inspection result.
     *
     * <p>Per-field status is derived from the violations raised against that field rather than
     * stored separately, so the field verdicts and the violation list can never disagree.
     */
    public InspectionResponse toResponse(Inspection inspection,
                                         List<ExtractedField> fields,
                                         List<Violation> violations,
                                         RiskScore riskScore) {

        Map<String, ComplianceStatus> statusByField = fieldStatuses(violations);

        List<ExtractedFieldResponse> fieldResponses = fields.stream()
                .map(field -> new ExtractedFieldResponse(
                        field.getId(),
                        field.getFieldName(),
                        field.getFieldValue(),
                        field.getConfidence(),
                        statusByField.getOrDefault(field.getFieldName(), ComplianceStatus.COMPLIANT),
                        BoundingBoxResponse.of(
                                field.getBoundingBoxX(),
                                field.getBoundingBoxY(),
                                field.getBoundingBoxWidth(),
                                field.getBoundingBoxHeight())))
                .toList();

        List<ViolationResponse> violationResponses = violations.stream()
                .map(violationMapper::toResponse)
                .toList();

        return new InspectionResponse(
                inspection.getId(),
                inspection.getStatus(),
                inspection.getOverallConfidence(),
                inspection.getRiskScore(),
                inspection.getRiskLevel(),
                inspection.getRulesetVersion(),
                inspection.getAiProvider(),
                inspection.getImageUrl(),
                productMapper.toResponse(inspection.getProduct()),
                inspection.getInspector() == null ? null : inspection.getInspector().getId(),
                inspection.getInspector() == null ? null : inspection.getInspector().getName(),
                inspection.getNotes(),
                inspection.getFailureReason(),
                fieldResponses,
                violationResponses,
                toRiskBreakdown(riskScore),
                inspection.getCreatedAt(),
                inspection.getCompletedAt()
        );
    }

    public InspectionSummaryResponse toSummary(Inspection inspection) {
        return new InspectionSummaryResponse(
                inspection.getId(),
                inspection.getStatus(),
                inspection.getOverallConfidence(),
                inspection.getRiskScore(),
                inspection.getRiskLevel(),
                inspection.getRulesetVersion(),
                inspection.getProduct() == null ? null : inspection.getProduct().getId(),
                inspection.getProduct() == null ? null : inspection.getProduct().getProductName(),
                inspection.getProduct() == null ? null : inspection.getProduct().getBrand(),
                inspection.getProduct() == null ? null : inspection.getProduct().getCategory(),
                inspection.getInspector() == null ? null : inspection.getInspector().getId(),
                inspection.getInspector() == null ? null : inspection.getInspector().getName(),
                inspection.getImageUrl(),
                inspection.getCreatedAt(),
                inspection.getCompletedAt()
        );
    }

    public RiskBreakdownResponse toRiskBreakdown(RiskScore score) {
        if (score == null) {
            return null;
        }
        return new RiskBreakdownResponse(
                score.getPreviousViolations(),
                score.getProductChanges(),
                score.getOnlineMismatch(),
                score.getCategoryRisk(),
                score.getRepeatIssue(),
                score.getTotalScore(),
                score.getRiskLevel(),
                score.getExplanation()
        );
    }

    /** Worst outcome wins per field: NON_COMPLIANT beats INCONCLUSIVE beats COMPLIANT. */
    private Map<String, ComplianceStatus> fieldStatuses(List<Violation> violations) {
        Map<String, ComplianceStatus> statuses = new HashMap<>();
        for (Violation violation : violations) {
            ComplianceStatus status = violation.getStatus() == ViolationStatus.NON_COMPLIANT
                    ? ComplianceStatus.NON_COMPLIANT
                    : ComplianceStatus.INCONCLUSIVE;
            statuses.merge(violation.getFieldName(), status, ComplianceStatus::worst);
        }
        return statuses;
    }
}
