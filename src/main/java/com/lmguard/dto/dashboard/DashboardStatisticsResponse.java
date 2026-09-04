package com.lmguard.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DashboardStatistics", description = "Aggregate inspection and compliance figures")
public record DashboardStatisticsResponse(

        @Schema(example = "412") long totalInspections,
        @Schema(example = "263") long compliantCount,
        @Schema(example = "118") long nonCompliantCount,
        @Schema(example = "27")  long inconclusiveCount,
        @Schema(example = "4")   long pendingCount,

        @Schema(description = "Inspections opened in the last 24 hours", example = "18")
        long inspectionsLast24h,

        @Schema(description = "Inspections opened in the last 7 days", example = "96")
        long inspectionsLast7Days,

        @Schema(description = "Non-compliant share of decided inspections, 0..100", example = "28.6")
        double nonComplianceRate,

        @Schema(description = "Mean risk score across scored inspections", example = "41.2")
        double averageRiskScore,

        @Schema(example = "31") long lowRiskCount,
        @Schema(example = "52") long mediumRiskCount,
        @Schema(example = "35") long highRiskCount,

        @Schema(description = "Registered products", example = "204")
        long totalProducts,

        @Schema(description = "Registered inspectors", example = "12")
        long totalInspectors,

        @Schema(description = "Most frequently breached rules, most frequent first")
        List<RuleBreachCount> topViolatedRules,

        @Schema(description = "Most frequently missing or invalid declarations")
        List<FieldBreachCount> topViolatedFields
) {

    @Schema(name = "RuleBreachCount", description = "How often one rule has been breached")
    public record RuleBreachCount(
            @Schema(example = "DEMO-RULE-001") String ruleCode,
            @Schema(example = "58") long count
    ) {
    }

    @Schema(name = "FieldBreachCount", description = "How often one declaration has been found missing or invalid")
    public record FieldBreachCount(
            @Schema(example = "CONSUMER_CARE") String fieldName,
            @Schema(example = "73") long count
    ) {
    }
}
