package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.dto.inspection.EvidenceResponse;
import com.lmguard.mapper.EvidenceMapper;
import com.lmguard.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/violations")
@RequiredArgsConstructor
@Tag(name = "4. Evidence", description = "Image-backed proof for individual findings")
public class EvidenceController {

    private final InspectionService inspectionService;
    private final EvidenceMapper evidenceMapper;

    @GetMapping("/{id}/evidence")
    @Operation(summary = "Evidence for one violation",
            description = """
                    Returns the image regions recorded for a single violation.

                    Coordinates are null when the finding is an absence: there is no region to
                    point at when a declaration is simply not on the package. That is still
                    recorded as evidence - "we looked and found nothing" is a claim that needs
                    to be attributable.
                    """)
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> forViolation(@PathVariable UUID id) {
        List<EvidenceResponse> evidence = inspectionService.evidenceForViolation(id).stream()
                .map(evidenceMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(evidence));
    }
}
