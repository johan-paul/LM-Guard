package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.common.PageResponse;
import com.lmguard.dto.violation.ViolationDecisionRequest;
import com.lmguard.dto.violation.ViolationDetailResponse;
import com.lmguard.dto.violation.ViolationSummaryResponse;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.ViolationCaseStatus;
import com.lmguard.security.SecurityUtils;
import com.lmguard.service.ViolationCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/violations")
@RequiredArgsConstructor
@Tag(name = "8. Violations", description = "The case queue: every rule breach the engine raised, "
        + "tracked through an inspector's review to a final case decision.")
public class ViolationController {

    private final ViolationCaseService violationCaseService;

    @GetMapping
    @Operation(summary = "The case queue",
            description = "Newest first. `since` is a rolling window in days (e.g. 7/30/90); "
                    + "free-text search over product/rule/finding is applied by the caller against this page.")
    public ResponseEntity<ApiResponse<PageResponse<ViolationSummaryResponse>>> search(
            @RequestParam(required = false) ViolationCaseStatus caseStatus,
            @RequestParam(required = false) RiskLevel riskLevel,
            @Parameter(description = "Only violations detected in the last N days") @RequestParam(required = false) Integer sinceDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Instant since = sinceDays == null ? null : Instant.now().minus(Duration.ofDays(sinceDays));
        var result = violationCaseService.search(caseStatus, riskLevel, since, PageRequest.of(page, Math.min(size, 200)));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "The full case for one violation")
    public ResponseEntity<ApiResponse<ViolationDetailResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(violationCaseService.detail(id)));
    }

    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Record an inspector decision on a case",
            description = "ADMIN only. The rule engine never closes a case; recording a decision here "
                    + "attributes it to the administrator's account.")
    public ResponseEntity<ApiResponse<ViolationDetailResponse>> decide(
            @PathVariable UUID id,
            @Valid @RequestBody ViolationDecisionRequest request) {
        var result = violationCaseService.decide(id, request, SecurityUtils.currentUserId());
        return ResponseEntity.ok(ApiResponse.success("Case decision recorded", result));
    }
}
