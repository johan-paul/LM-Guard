package com.lmguard.service;

import com.lmguard.config.properties.RiskProperties;
import com.lmguard.dto.dashboard.AnalyticsResponse;
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
import com.lmguard.repository.InspectorProfileRepository;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
    private static final int TREND_DAYS = 90;
    private static final int OFFENDER_TREND_MONTHS = 6;
    private static final int REPEAT_OFFENDER_THRESHOLD = 2;
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Kolkata");

    private final InspectionRepository inspectionRepository;
    private final ViolationRepository violationRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InspectorProfileRepository inspectorProfileRepository;
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
                + inspectionRepository.countByStatus(InspectionStatus.IN_PROGRESS)
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
                repeatOffenderCount(),
                violationRepository.countByCaseStatus(com.lmguard.entity.enums.ViolationCaseStatus.ESCALATED),
                avgResolutionDays(),
                topRules(),
                topFields());
    }

    @Transactional(readOnly = true)
    public List<InspectionSummaryResponse> recentInspections() {
        Pageable pageable = PageRequest.of(0, RECENT_INSPECTION_COUNT, Sort.by(Sort.Direction.DESC, "createdAt"));
        return inspectionRepository.searchDetailed(null, null, null, null, pageable)
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
        java.util.UUID productId = score.getProduct() == null ? null : score.getProduct().getId();
        long previousViolations = productId == null ? 0
                : violationRepository.countByInspection_Product_IdAndStatus(
                        productId, com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT);

        return new HighRiskProductResponse(
                productId,
                score.getProduct() == null ? null : score.getProduct().getProductName(),
                score.getProduct() == null ? null : score.getProduct().getBrand(),
                score.getProduct() == null ? null : score.getProduct().getCategory(),
                score.getInspection() == null ? null : score.getInspection().getId(),
                score.getTotalScore(),
                score.getRiskLevel(),
                score.getExplanation(),
                score.getCreatedAt(),
                previousViolations);
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

    /**
     * Programme-level reporting figures for the Analytics screen. Every number here is a real
     * aggregate over inspection/violation/risk-score rows - nothing here is randomised or backfilled.
     */
    @Transactional(readOnly = true)
    public AnalyticsResponse analytics() {
        return new AnalyticsResponse(
                dailyTrend(),
                topFields(),
                riskScoreRepository.countByRiskLevel(RiskLevel.LOW),
                riskScoreRepository.countByRiskLevel(RiskLevel.MEDIUM),
                riskScoreRepository.countByRiskLevel(RiskLevel.HIGH),
                avgResolutionDays(),
                regionalInsights(),
                confidenceBands(),
                offenderTrend());
    }

    private List<AnalyticsResponse.DailyTrendPoint> dailyTrend() {
        Instant since = Instant.now().minus(Duration.ofDays(TREND_DAYS - 1L));

        Map<LocalDate, Long> inspectionsByDay = inspectionRepository.findCreatedAtSince(since).stream()
                .collect(Collectors.groupingBy(at -> LocalDate.ofInstant(at, REPORTING_ZONE), Collectors.counting()));
        Map<LocalDate, Long> violationsByDay = violationRepository.findCreatedAtSinceNonCompliant(since).stream()
                .collect(Collectors.groupingBy(at -> LocalDate.ofInstant(at, REPORTING_ZONE), Collectors.counting()));

        LocalDate today = LocalDate.now(REPORTING_ZONE);
        DateTimeFormatter label = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
        List<AnalyticsResponse.DailyTrendPoint> points = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            points.add(new AnalyticsResponse.DailyTrendPoint(
                    day.toString(),
                    day.format(label),
                    inspectionsByDay.getOrDefault(day, 0L),
                    violationsByDay.getOrDefault(day, 0L)));
        }
        return points;
    }

    private double avgResolutionDays() {
        List<Object[]> rows = inspectionRepository.findCreatedAndCompletedForDecided();
        if (rows.isEmpty()) {
            return 0.0;
        }
        double totalDays = 0;
        for (Object[] row : rows) {
            Instant created = (Instant) row[0];
            Instant completed = (Instant) row[1];
            totalDays += Duration.between(created, completed).toMinutes() / 1440.0;
        }
        return round1(totalDays / rows.size());
    }

    private List<AnalyticsResponse.RegionalInsight> regionalInsights() {
        Map<String, Long> inspectorsByZone = inspectorProfileRepository.countGroupedByZoneName().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));

        return inspectionRepository.countAndViolationsByZone().stream()
                .map(row -> {
                    String zone = (String) row[0];
                    long total = ((Number) row[1]).longValue();
                    long violations = ((Number) row[2]).longValue();
                    double complianceRate = total == 0 ? 0.0 : round1((total - violations) * 100.0 / total);
                    return new AnalyticsResponse.RegionalInsight(
                            zone, total, violations, complianceRate, inspectorsByZone.getOrDefault(zone, 0L));
                })
                .sorted(Comparator.comparingLong(AnalyticsResponse.RegionalInsight::inspections).reversed())
                .toList();
    }

    private List<AnalyticsResponse.ConfidenceBand> confidenceBands() {
        List<BigDecimal> values = violationRepository.findConfidenceValues();
        long high = values.stream().filter(v -> v.doubleValue() >= 0.90).count();
        long mid = values.stream().filter(v -> v.doubleValue() >= 0.70 && v.doubleValue() < 0.90).count();
        long low = values.stream().filter(v -> v.doubleValue() >= 0.50 && v.doubleValue() < 0.70).count();
        long veryLow = values.stream().filter(v -> v.doubleValue() < 0.50).count();
        return List.of(
                new AnalyticsResponse.ConfidenceBand("90-100%", high),
                new AnalyticsResponse.ConfidenceBand("70-89%", mid),
                new AnalyticsResponse.ConfidenceBand("50-69%", low),
                new AnalyticsResponse.ConfidenceBand("Below 50%", veryLow));
    }

    /**
     * A manufacturer becomes a "tracked offender" in the month its {@value #REPEAT_OFFENDER_THRESHOLD}nd
     * substantiated violation lands, and stays tracked every month after. "New" counts only the
     * month the threshold was actually crossed.
     */
    private List<AnalyticsResponse.OffenderTrendPoint> offenderTrend() {
        Map<String, List<Instant>> violationDatesByBrand = new HashMap<>();
        for (Object[] row : violationRepository.findBrandAndCreatedAtNonCompliant()) {
            violationDatesByBrand.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add((Instant) row[1]);
        }

        Map<String, YearMonth> thresholdMonthByBrand = new HashMap<>();
        for (Map.Entry<String, List<Instant>> entry : violationDatesByBrand.entrySet()) {
            List<Instant> dates = entry.getValue();
            if (dates.size() < REPEAT_OFFENDER_THRESHOLD) {
                continue;
            }
            dates.sort(Comparator.naturalOrder());
            Instant thresholdCrossing = dates.get(REPEAT_OFFENDER_THRESHOLD - 1);
            thresholdMonthByBrand.put(entry.getKey(), YearMonth.from(thresholdCrossing.atZone(REPORTING_ZONE)));
        }

        YearMonth currentMonth = YearMonth.now(REPORTING_ZONE);
        List<AnalyticsResponse.OffenderTrendPoint> points = new ArrayList<>(OFFENDER_TREND_MONTHS);
        for (int i = OFFENDER_TREND_MONTHS - 1; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            long tracked = thresholdMonthByBrand.values().stream().filter(m -> !m.isAfter(month)).count();
            long newThisMonth = thresholdMonthByBrand.values().stream().filter(month::equals).count();
            points.add(new AnalyticsResponse.OffenderTrendPoint(
                    month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), tracked, newThisMonth));
        }
        return points;
    }

    /** Manufacturers with a sustained substantiated-violation history, most-cited first. */
    @Transactional(readOnly = true)
    public List<com.lmguard.dto.dashboard.RepeatOffenderResponse> repeatOffenders() {
        Map<String, Long> topZoneCountByBrand = new HashMap<>();
        Map<String, String> topZoneByBrand = new HashMap<>();
        for (Object[] row : violationRepository.countByBrandAndZone()) {
            String brand = (String) row[0];
            String zone = (String) row[1];
            long count = ((Number) row[2]).longValue();
            if (count > topZoneCountByBrand.getOrDefault(brand, -1L)) {
                topZoneCountByBrand.put(brand, count);
                topZoneByBrand.put(brand, zone);
            }
        }

        return violationRepository.countAndProductsByBrand().stream()
                .filter(row -> ((Number) row[1]).longValue() >= REPEAT_OFFENDER_THRESHOLD)
                .map(row -> {
                    String brand = (String) row[0];
                    return new com.lmguard.dto.dashboard.RepeatOffenderResponse(
                            brand,
                            ((Number) row[1]).longValue(),
                            ((Number) row[2]).longValue(),
                            topZoneByBrand.getOrDefault(brand, "—"));
                })
                .toList();
    }

    private long repeatOffenderCount() {
        return violationRepository.countAndProductsByBrand().stream()
                .filter(row -> ((Number) row[1]).longValue() >= REPEAT_OFFENDER_THRESHOLD)
                .count();
    }

    /** The composite risk score's weighted contributors, as actually configured - not a
     * hardcoded guess at what the weights might be. */
    @Transactional(readOnly = true)
    public List<com.lmguard.dto.dashboard.RiskFactorResponse> riskFactors() {
        RiskProperties.Weights weights = riskProperties.weights();
        int maxScore = riskProperties.maxScore();

        return List.of(
                riskFactor("Repeated declaration violations", weights.previousViolations(), maxScore,
                        riskScoreRepository.countByPreviousViolationsGreaterThan(0)),
                riskFactor("Physical–digital mismatch", weights.onlineMismatch(), maxScore,
                        riskScoreRepository.countByOnlineMismatchGreaterThan(0)),
                riskFactor("Recent package changes", weights.productChanges(), maxScore,
                        riskScoreRepository.countByProductChangesGreaterThan(0)),
                riskFactor("Product category risk", weights.categoryRisk(), maxScore,
                        riskScoreRepository.countByCategoryRiskGreaterThan(0)),
                riskFactor("Same rule failing again", weights.repeatIssue(), maxScore,
                        riskScoreRepository.countByRepeatIssueGreaterThan(0)));
    }

    private com.lmguard.dto.dashboard.RiskFactorResponse riskFactor(String factor, int weight, int maxScore, long occurrences) {
        int sharePercent = maxScore == 0 ? 0 : (int) Math.round(weight * 100.0 / maxScore);
        return new com.lmguard.dto.dashboard.RiskFactorResponse(factor, sharePercent, occurrences);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
