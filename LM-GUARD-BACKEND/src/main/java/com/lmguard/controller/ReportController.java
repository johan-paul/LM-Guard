package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
@Tag(name = "6. Reports", description = "Structured inspection reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/{id}/report")
    @Operation(summary = "Inspection report",
            description = """
                    A structured report for one inspection: declarations examined, findings with
                    their evidence, the risk breakdown, and a summary.

                    Two things are always included, whatever the verdict - the exact ruleset
                    version the decision was made under, and a notice that the result is
                    advisory and the enforcement decision belongs to the inspector.

                    Returned as JSON so the frontend can render or print it; a server-side PDF
                    writer can consume this same structure later.
                    """)
    public ResponseEntity<ApiResponse<Map<String, Object>>> report(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(reportService.buildReport(id)));
    }
}
