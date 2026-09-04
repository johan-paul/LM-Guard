package com.lmguard.service;

import com.lmguard.config.properties.RiskProperties;
import com.lmguard.dto.dashboard.DashboardResponse;
import com.lmguard.dto.dashboard.DashboardStatisticsResponse;
import com.lmguard.dto.dashboard.HighRiskProductResponse;
import com.lmguard.dto.inspection.InspectionSummaryResponse;
import com.lmguard.entity.RiskScore;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.Role;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.ProductRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.UserRepository;
import com.lmguard.repository.ViolationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Aggregate figures for the inspector and administrator dashboards.
 *
 * <p>Counting is pushed into SQL rather than done by loading rows into memory, so the numbers
 * stay correct and cheap as the inspection table grows.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_INSPECTION_COUNT = 10;
    private static final int TOP_N = 5;

    private final InspectionRepository inspectionRepository;
    private final ViolationRepository violationRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InspectionMapper inspectionMapper;
    private final RiskProperties riskProperties;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        return new DashboardResponse(
                statistics(),
                recentInspections(),
                highRiskProducts(PageRequest.of(0, RECENT_INSPECTION_COUNT)).getContent());
    }

    @Transactional(readOnly = true)
    public DashboardStatisticsResponse statistics() {
        long total = inspectionRepository.count();
        long compliant = inspectionRepository.countByStatus(InspectionStatus.COMPLIANT);
        long nonCompliant = inspectionRepository.countByStatus(InspectionStatus.NON_COMPLIANT);
        long inconclusive = inspectionRepository.countByStatus(InspectionStatus.INCONCLUSIVE);
        long pending = inspectionRepository.countByStatus(InspectionStatus.PENDING)
                + inspectionRepository.countByStatus(InspectionStatus.PROCESSING);

        // Rate is over DECIDED inspections only. Including pending ones would make the
        // compliance picture look better simply because a backlog exists.
        long decided = compliant + nonCompliant + inconclusive;
        double nonComplianceRate = decided == 0 ? 0.0 : round1(nonCompliant * 100.0 / decided);

        Double averageRisk = inspectionRepository.findAverageRiskScore();

        return new DashboardStatisticsResponse(
                total,
                compliant,
                nonCompliant,
                inconclusive,
                pending,
                inspectionRepository.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(1))),
                inspectionRepository.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(7))),
                nonComplianceRate,
                averageRisk == null ? 0.0 : round1(averageRisk),
                riskScoreRepository.countByRiskLevel(RiskLevel.LOW),
                riskScoreRepository.countByRiskLevel(RiskLevel.MEDIUM),
                riskScoreRepository.countByRiskLevel(RiskLevel.HIGH),
                productRepository.count(),
                userRepository.countByRole(Role.INSPECTOR),
                topRules(),
                topFields());
    }

    @Transactional(readOnly = true)
    public List<InspectionSummaryResponse> recentInspections() {
        Pageable pageable = PageRequest.of(0, RECENT_INSPECTION_COUNT, Sort.by(Sort.Direction.DESC, "createdAt"));
        return inspectionRepository.searchDetailed(null, null, null, pageable)
                .map(inspectionMapper::toSummary)
                .getContent();
    }

    @Transactional(readOnly = true)
    public Page<HighRiskProductResponse> highRiskProducts(Pageable pageable) {
        return riskScoreRepository
                .findHighRisk(riskProperties.thresholds().mediumMax() + 1, pageable)
                .map(this::toHighRisk);
    }

    private HighRiskProductResponse toHighRisk(RiskScore score) {
        return new HighRiskProductResponse(
                score.getProduct() == null ? null : score.getProduct().getId(),
                score.getProduct() == null ? null : score.getProduct().getProductName(),
                score.getProduct() == null ? null : score.getProduct().getBrand(),
                score.getProduct() == null ? null : score.getProduct().getCategory(),
                score.getInspection() == null ? null : score.getInspection().getId(),
                score.getTotalScore(),
                score.getRiskLevel(),
                score.getExplanation(),
                score.getCreatedAt());
    }

    private List<DashboardStatisticsResponse.RuleBreachCount> topRules() {
        return violationRepository.countGroupedByRuleCode().stream()
                .limit(TOP_N)
                .map(row -> new DashboardStatisticsResponse.RuleBreachCount(
                        (String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    private List<DashboardStatisticsResponse.FieldBreachCount> topFields() {
        return violationRepository.countGroupedByFieldName().stream()
                .limit(TOP_N)
                .map(row -> new DashboardStatisticsResponse.FieldBreachCount(
                        (String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
