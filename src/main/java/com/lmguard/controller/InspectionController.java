package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.common.PageResponse;
import com.lmguard.dto.inspection.EvidenceResponse;
import com.lmguard.dto.inspection.ImageUploadResponse;
import com.lmguard.dto.inspection.InspectionCreateRequest;
import com.lmguard.dto.inspection.InspectionResponse;
import com.lmguard.dto.inspection.InspectionSummaryResponse;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.mapper.EvidenceMapper;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.security.SecurityUtils;
import com.lmguard.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
@Tag(name = "3. Inspections", description = "The core workflow: create, upload, analyse, read")
public class InspectionController {

    private final InspectionService inspectionService;
    private final InspectionMapper inspectionMapper;
    private final EvidenceMapper evidenceMapper;

    @PostMapping
    @Operation(summary = "Open an inspection",
            description = """
                    Creates an inspection in `PENDING` state.

                    Pass `productId` for a known product, or `productName` (plus optional brand,
                    category, barcode) to register one inline - a field inspector scanning an
                    unfamiliar package should not have to register it first.

                    Next: `POST /api/inspections/{id}/image`, then `POST /api/inspections/{id}/analyze`.
                    """)
    public ResponseEntity<ApiResponse<InspectionResponse>> create(
            @Valid @RequestBody InspectionCreateRequest request) {

        InspectionResponse response = inspectionService.create(request, SecurityUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Inspection created", response));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the package image",
            description = """
                    Stores the image in Supabase Storage (or on local disk when
                    `STORAGE_PROVIDER=local`) and records its URL on the inspection. The image
                    bytes never go into PostgreSQL.

                    Accepts JPEG, PNG, WebP and HEIC. Re-uploading replaces the previous image
                    and deletes it from the bucket.
                    """)
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImage(
            @PathVariable UUID id,
            @Parameter(description = "The package photograph")
            @RequestPart("file") MultipartFile file) {

        return ResponseEntity.ok(ApiResponse.success("Image stored", inspectionService.uploadImage(id, file)));
    }

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Run the analysis pipeline",
            description = """
                    Runs the full pipeline and returns the complete result:

                    `AI facts -> extracted fields -> deterministic rule engine -> violations ->
                    evidence -> risk score`

                    The AI extracts what is visible; the rule engine alone decides compliance.
                    A declaration read with low confidence yields `INCONCLUSIVE`, never a
                    violation. The exact `rulesetVersion` used is recorded on the inspection.

                    With `AI_MOCK_MODE=true` this works end to end with no external AI service.
                    Calling it again re-runs the analysis and replaces the previous findings.
                    """)
    public ResponseEntity<ApiResponse<InspectionResponse>> analyze(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success("Inspection completed successfully", inspectionService.analyze(id)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the full inspection result",
            description = "Verdict, extracted declarations with per-field status, violations, "
                    + "evidence regions and the itemised risk breakdown.")
    public ResponseEntity<ApiResponse<InspectionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(inspectionService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List and filter inspections")
    public ResponseEntity<ApiResponse<PageResponse<InspectionSummaryResponse>>> list(
            @Parameter(description = "Filter by status") @RequestParam(required = false) InspectionStatus status,
            @Parameter(description = "Filter by product") @RequestParam(required = false) UUID productId,
            @Parameter(description = "Filter by inspector") @RequestParam(required = false) UUID inspectorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var results = inspectionService.search(status, productId, inspectorId,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(results, inspectionMapper::toSummary)));
    }

    @GetMapping("/{id}/evidence")
    @Operation(summary = "All evidence for an inspection",
            description = "Every image region recorded across this inspection's findings. "
                    + "The frontend overlays these on the package image.")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> evidence(@PathVariable UUID id) {
        List<EvidenceResponse> evidence = inspectionService.evidenceForInspection(id).stream()
                .map(evidenceMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(evidence));
    }
}
