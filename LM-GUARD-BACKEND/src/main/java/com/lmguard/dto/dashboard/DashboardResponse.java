package com.lmguard.dto.dashboard;

import com.lmguard.dto.inspection.InspectionSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Dashboard", description = "Headline figures plus recent activity for the landing screen")
public record DashboardResponse(

        @Schema(description = "Aggregate counts and rates")
        DashboardStatisticsResponse statistics,

        @Schema(description = "Ten most recent inspections, newest first")
        List<InspectionSummaryResponse> recentInspections,

        @Schema(description = "Products currently sitting in the HIGH risk band")
        List<HighRiskProductResponse> highRiskProducts
) {
}
