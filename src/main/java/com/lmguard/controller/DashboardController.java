package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.common.PageResponse;
import com.lmguard.dto.dashboard.DashboardResponse;
import com.lmguard.dto.dashboard.DashboardStatisticsResponse;
import com.lmguard.dto.dashboard.HighRiskProductResponse;
import com.lmguard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "5. Dashboard", description = "Aggregate figures and triage lists")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Dashboard overview",
            description = "Headline statistics, the ten most recent inspections, and the current "
                    + "high-risk products, in one call.")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.dashboard()));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Aggregate statistics",
            description = "The non-compliance rate is calculated over **decided** inspections only, "
                    + "so a backlog of pending work cannot flatter the numbers.")
    public ResponseEntity<ApiResponse<DashboardStatisticsResponse>> statistics() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.statistics()));
    }

    @GetMapping("/high-risk")
    @Operation(summary = "Products flagged for follow-up",
            description = "Scored above the MEDIUM band, highest first. Each entry carries the "
                    + "explanation of how its score was reached.")
    public ResponseEntity<ApiResponse<PageResponse<HighRiskProductResponse>>> highRisk(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                dashboardService.highRiskProducts(PageRequest.of(page, Math.min(size, 100))))));
    }
}
