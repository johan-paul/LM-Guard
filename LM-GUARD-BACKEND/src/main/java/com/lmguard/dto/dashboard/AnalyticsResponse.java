package com.lmguard.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Programme-level reporting figures for the Analytics screen: a daily activity series, the
 * risk and confidence distributions, and per-zone / per-manufacturer breakdowns.
 *
 * <p>Every figure here is a real aggregate over inspection/violation/risk-score data - there is
 * no synthetic or randomised series. A metric the current data model cannot support (for example
 * a resolution funnel with enforcement-action stages this schema doesn't track) is simply not
 * included rather than approximated.
 */
@Schema(name = "Analytics", description = "Programme-level compliance analytics")
public record AnalyticsResponse(

        @Schema(description = "Daily case-open and violation-detection counts for the last 90 days")
        List<DailyTrendPoint> dailyTrend,

        @Schema(description = "Most frequently missing or invalid declarations (same source as the dashboard's topViolatedFields)")
        List<DashboardStatisticsResponse.FieldBreachCount> violationCategories,

        @Schema(example = "31") long lowRiskCount,
        @Schema(example = "52") long mediumRiskCount,
        @Schema(example = "35") long highRiskCount,

        @Schema(description = "Mean days between an inspection opening and its final decision, over decided inspections only")
        double avgResolutionDays,

        @Schema(description = "Case volume, violations and officer headcount per zone")
        List<RegionalInsight> regional,

        @Schema(description = "Substantiated-violation counts bucketed by the confidence the observation was made at")
        List<ConfidenceBand> confidenceBands,

        @Schema(description = "Monthly count of manufacturers carrying 2+ substantiated violations, and how many crossed that threshold that month")
        List<OffenderTrendPoint> offenderTrend

) {
    @Schema(name = "DailyTrendPoint")
    public record DailyTrendPoint(String date, String label, long inspections, long violations) {
    }

    @Schema(name = "RegionalInsight")
    public record RegionalInsight(String zone, long inspections, long violations, double complianceRate, long inspectors) {
    }

    @Schema(name = "ConfidenceBand")
    public record ConfidenceBand(String band, long count) {
    }

    @Schema(name = "OffenderTrendPoint")
    public record OffenderTrendPoint(String month, long offenders, long newOffenders) {
    }
}
