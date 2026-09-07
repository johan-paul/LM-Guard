package com.lmguard.service;

import com.lmguard.config.properties.RiskProperties;
import com.lmguard.dto.dashboard.AnalyticsResponse;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.InspectorProfileRepository;
import com.lmguard.repository.ProductRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.UserRepository;
import com.lmguard.repository.ViolationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The Analytics screen's aggregate figures: every number here must be a real derivation over
 * inspection/violation rows, never a randomised or fabricated series. This test pins the
 * trickiest derivations - daily bucketing, average resolution time, per-zone compliance rate,
 * confidence banding and the repeat-offender month-crossing logic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Dashboard service - analytics")
class DashboardServiceAnalyticsTest {

    @Mock private InspectionRepository inspectionRepository;
    @Mock private ViolationRepository violationRepository;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private InspectorProfileRepository inspectorProfileRepository;
    @Mock private InspectionMapper inspectionMapper;
    @Mock private RiskProperties riskProperties;

    private DashboardService service() {
        return new DashboardService(inspectionRepository, violationRepository, riskScoreRepository,
                productRepository, userRepository, inspectorProfileRepository, inspectionMapper, riskProperties);
    }

    @Test
    @DisplayName("dailyTrend buckets timestamps by calendar day and fills unrepresented days with zero")
    void dailyTrendBucketsByDay() {
        Instant today = Instant.now();
        Instant yesterday = today.minus(1, ChronoUnit.DAYS);

        when(inspectionRepository.findCreatedAtSince(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(today, today, yesterday));
        when(violationRepository.findCreatedAtSinceNonCompliant(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(today));
        when(violationRepository.countGroupedByFieldName()).thenReturn(List.of());
        when(inspectionRepository.findCreatedAndCompletedForDecided()).thenReturn(List.of());
        when(inspectionRepository.countAndViolationsByZone()).thenReturn(List.of());
        when(inspectorProfileRepository.countGroupedByZoneName()).thenReturn(List.of());
        when(violationRepository.findConfidenceValues()).thenReturn(List.of());
        when(violationRepository.findBrandAndCreatedAtNonCompliant()).thenReturn(List.of());

        AnalyticsResponse response = service().analytics();

        assertThat(response.dailyTrend()).hasSize(90);
        AnalyticsResponse.DailyTrendPoint last = response.dailyTrend().get(89);
        AnalyticsResponse.DailyTrendPoint secondLast = response.dailyTrend().get(88);
        assertThat(last.inspections()).isEqualTo(2);
        assertThat(last.violations()).isEqualTo(1);
        assertThat(secondLast.inspections()).isEqualTo(1);
        assertThat(secondLast.violations()).isEqualTo(0);
    }

    @Test
    @DisplayName("avgResolutionDays averages (completedAt - createdAt) over decided inspections only")
    void avgResolutionDaysAverages() {
        stubEmptyExcept();
        Instant now = Instant.now();
        when(inspectionRepository.findCreatedAndCompletedForDecided()).thenReturn(List.of(
                new Object[]{now.minus(2, ChronoUnit.DAYS), now},
                new Object[]{now.minus(4, ChronoUnit.DAYS), now}));

        AnalyticsResponse response = service().analytics();

        assertThat(response.avgResolutionDays()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("regionalInsights computes compliance rate as (total - violations) / total")
    void regionalInsightsComputesComplianceRate() {
        stubEmptyExcept();
        when(inspectionRepository.countAndViolationsByZone()).thenReturn(List.<Object[]>of(
                new Object[]{"Coimbatore North", 100L, 25L}));
        when(inspectorProfileRepository.countGroupedByZoneName()).thenReturn(List.<Object[]>of(
                new Object[]{"Coimbatore North", 4L}));

        AnalyticsResponse response = service().analytics();

        assertThat(response.regional()).hasSize(1);
        AnalyticsResponse.RegionalInsight insight = response.regional().get(0);
        assertThat(insight.zone()).isEqualTo("Coimbatore North");
        assertThat(insight.inspections()).isEqualTo(100L);
        assertThat(insight.violations()).isEqualTo(25L);
        assertThat(insight.complianceRate()).isEqualTo(75.0);
        assertThat(insight.inspectors()).isEqualTo(4L);
    }

    @Test
    @DisplayName("confidenceBands sorts substantiated-violation confidences into the four bands")
    void confidenceBandsBucketsCorrectly() {
        stubEmptyExcept();
        when(violationRepository.findConfidenceValues()).thenReturn(List.of(
                new BigDecimal("0.95"), new BigDecimal("0.75"), new BigDecimal("0.55"), new BigDecimal("0.30")));

        AnalyticsResponse response = service().analytics();

        assertThat(response.confidenceBands()).extracting(AnalyticsResponse.ConfidenceBand::count)
                .containsExactly(1L, 1L, 1L, 1L);
    }

    @Test
    @DisplayName("offenderTrend marks a manufacturer tracked from the month its 2nd violation lands, onward")
    void offenderTrendTracksFromThresholdMonthOnward() {
        stubEmptyExcept();
        Instant fourMonthsAgo = Instant.now().minus(120, ChronoUnit.DAYS);
        Instant threeMonthsAgo = Instant.now().minus(90, ChronoUnit.DAYS);
        when(violationRepository.findBrandAndCreatedAtNonCompliant()).thenReturn(List.of(
                new Object[]{"Acme Foods", fourMonthsAgo},
                new Object[]{"Acme Foods", threeMonthsAgo}));

        AnalyticsResponse response = service().analytics();

        long trackedInLastMonth = response.offenderTrend().get(response.offenderTrend().size() - 1).offenders();
        long totalNew = response.offenderTrend().stream().mapToLong(AnalyticsResponse.OffenderTrendPoint::newOffenders).sum();
        assertThat(trackedInLastMonth).isEqualTo(1L);
        assertThat(totalNew).isEqualTo(1L);
    }

    @Test
    @DisplayName("repeatOffenders keeps only manufacturers at or above the repeat threshold, with their predominant zone")
    void repeatOffendersFiltersByThreshold() {
        when(violationRepository.countAndProductsByBrand()).thenReturn(List.<Object[]>of(
                new Object[]{"Acme Foods", 5L, 2L},
                new Object[]{"Solo Brand", 1L, 1L}));
        when(violationRepository.countByBrandAndZone()).thenReturn(List.<Object[]>of(
                new Object[]{"Acme Foods", "Coimbatore North", 4L},
                new Object[]{"Acme Foods", "Erode", 1L}));

        List<com.lmguard.dto.dashboard.RepeatOffenderResponse> offenders = service().repeatOffenders();

        assertThat(offenders).hasSize(1);
        assertThat(offenders.get(0).manufacturer()).isEqualTo("Acme Foods");
        assertThat(offenders.get(0).totalViolations()).isEqualTo(5L);
        assertThat(offenders.get(0).trackedProducts()).isEqualTo(2L);
        assertThat(offenders.get(0).zone()).isEqualTo("Coimbatore North");
    }

    @Test
    @DisplayName("riskFactors reports the actually-configured weights as a share of maxScore, not a hardcoded guess")
    void riskFactorsReflectsConfiguredWeights() {
        when(riskProperties.weights()).thenReturn(new RiskProperties.Weights(30, 20, 25, 10, 15));
        when(riskProperties.maxScore()).thenReturn(100);
        when(riskScoreRepository.countByPreviousViolationsGreaterThan(0)).thenReturn(46L);

        List<com.lmguard.dto.dashboard.RiskFactorResponse> factors = service().riskFactors();

        assertThat(factors).hasSize(5);
        assertThat(factors.get(0).factor()).isEqualTo("Repeated declaration violations");
        assertThat(factors.get(0).weight()).isEqualTo(30);
        assertThat(factors.get(0).occurrences()).isEqualTo(46L);
    }

    @Test
    @DisplayName("riskDistribution passes through the risk-score repository's per-band counts unchanged")
    void riskDistributionPassesThroughCounts() {
        stubEmptyExcept();
        when(riskScoreRepository.countByRiskLevel(RiskLevel.LOW)).thenReturn(10L);
        when(riskScoreRepository.countByRiskLevel(RiskLevel.MEDIUM)).thenReturn(6L);
        when(riskScoreRepository.countByRiskLevel(RiskLevel.HIGH)).thenReturn(3L);

        AnalyticsResponse response = service().analytics();

        assertThat(response.lowRiskCount()).isEqualTo(10L);
        assertThat(response.mediumRiskCount()).isEqualTo(6L);
        assertThat(response.highRiskCount()).isEqualTo(3L);
    }

    private void stubEmptyExcept() {
        when(inspectionRepository.findCreatedAtSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(violationRepository.findCreatedAtSinceNonCompliant(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(violationRepository.countGroupedByFieldName()).thenReturn(List.of());
        when(inspectionRepository.findCreatedAndCompletedForDecided()).thenReturn(List.of());
        when(inspectionRepository.countAndViolationsByZone()).thenReturn(List.of());
        when(inspectorProfileRepository.countGroupedByZoneName()).thenReturn(List.of());
        when(violationRepository.findConfidenceValues()).thenReturn(List.of());
        when(violationRepository.findBrandAndCreatedAtNonCompliant()).thenReturn(List.of());
    }
}
