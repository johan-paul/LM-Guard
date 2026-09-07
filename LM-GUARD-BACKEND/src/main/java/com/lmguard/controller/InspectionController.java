package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.common.PageResponse;
import com.lmguard.dto.inspection.AssignmentHistoryResponse;
import com.lmguard.dto.inspection.ChecklistBulkUpsertRequest;
import com.lmguard.dto.inspection.ChecklistEntryResponse;
import com.lmguard.dto.inspection.EvidenceResponse;
import com.lmguard.dto.inspection.FindingCreateRequest;
import com.lmguard.dto.inspection.FindingResponse;
import com.lmguard.dto.inspection.ImageUploadResponse;
import com.lmguard.dto.inspection.InspectionAssignmentRequest;
import com.lmguard.dto.inspection.InspectionCreateRequest;
import com.lmguard.dto.inspection.InspectionEvidenceResponse;
import com.lmguard.dto.inspection.InspectionProductRequest;
import com.lmguard.dto.inspection.InspectionResponse;
import com.lmguard.dto.inspection.InspectionSubmitRequest;
import com.lmguard.dto.inspection.InspectionSummaryResponse;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.mapper.EvidenceMapper;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.security.SecurityUtils;
import com.lmguard.service.ChecklistService;
import com.lmguard.service.FindingService;
import com.lmguard.service.InspectionEvidenceService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ChecklistService checklistService;
    private final FindingService findingService;
    private final InspectionEvidenceService inspectionEvidenceService;
    private final InspectionMapper inspectionMapper;
    private final EvidenceMapper evidenceMapper;

    @PostMapping
    @Operation(summary = "Open an inspection",
            description = """
                    Creates an inspection in `PENDING` state. Two entry points share this one
                    endpoint and the same model:

                    - An inspector opening their own inspection directly: omit `inspectorId`.
                    - An admin opening a case and assigning it to a specific inspector: pass
                      `inspectorId` (ADMIN role required, rejected with 403 otherwise).

                    `zoneId` is independent of which of those this is and needs no special
                    permission either way. All of `productId`/`productName`/`zoneId` may be
                    omitted - product identification and zone tagging are steps in the workflow
                    that follows, not requirements for opening the case. Pass `productId` for a
                    known product, or `productName` (plus optional brand, category, barcode) to
                    register one inline.

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
            @Parameter(description = "Filter by zone") @RequestParam(required = false) UUID zoneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var results = inspectionService.search(status, productId, inspectorId, zoneId,
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

    @PatchMapping("/{id}/assignment")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign or reassign an inspection",
            description = "Admin-only. Moves the inspection to a different inspector and/or zone "
                    + "and records the change in the assignment audit trail.")
    public ResponseEntity<ApiResponse<InspectionResponse>> assign(
            @PathVariable UUID id,
            @Valid @RequestBody InspectionAssignmentRequest request) {
        InspectionResponse response =
                inspectionService.reassign(id, request, SecurityUtils.currentUserId());
        return ResponseEntity.ok(ApiResponse.success("Inspection reassigned", response));
    }

    @GetMapping("/{id}/assignment-history")
    @Operation(summary = "Assignment audit trail", description = "Every assignment and reassignment, newest first.")
    public ResponseEntity<ApiResponse<List<AssignmentHistoryResponse>>> assignmentHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(inspectionService.assignmentHistory(id)));
    }

    @PatchMapping("/{id}/product")
    @Operation(summary = "Identify/attach a product",
            description = "The product-identification step of the six-step workflow, for an "
                    + "inspection opened by an admin with no product yet.")
    public ResponseEntity<ApiResponse<InspectionResponse>> identifyProduct(
            @PathVariable UUID id,
            @Valid @RequestBody InspectionProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product identified", inspectionService.identifyProduct(id, request)));
    }

    @PatchMapping("/{id}/notes")
    @Operation(summary = "Save the inspector's working notes",
            description = "Persists notes for an in-progress inspection independent of submission, "
                    + "so they survive a draft save and are not lost if the app closes before the "
                    + "inspector submits.")
    public ResponseEntity<ApiResponse<InspectionResponse>> updateNotes(
            @PathVariable UUID id,
            @Valid @RequestBody com.lmguard.dto.inspection.InspectionNotesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Notes saved", inspectionService.updateNotes(id, request, SecurityUtils.currentUserId())));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit the inspector's final decision",
            description = """
                    The inspector's own, independent decision - COMPLIANT, NON_COMPLIANT or
                    INCONCLUSIVE. AI/rule-engine suggestions from `/analyze` are advisory only and
                    are never written here automatically; only this call sets the inspection's
                    authoritative outcome. Requires the checklist to be fully answered.
                    """)
    public ResponseEntity<ApiResponse<InspectionResponse>> submit(
            @PathVariable UUID id,
            @Valid @RequestBody InspectionSubmitRequest request) {
        InspectionResponse response = inspectionService.submit(id, request, SecurityUtils.currentUserId());
        return ResponseEntity.ok(ApiResponse.success("Inspection submitted", response));
    }

    @GetMapping("/{id}/checklist")
    @Operation(summary = "Get the compliance checklist")
    public ResponseEntity<ApiResponse<List<ChecklistEntryResponse>>> getChecklist(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(checklistService.get(id)));
    }

    @PutMapping("/{id}/checklist")
    @Operation(summary = "Save the compliance checklist",
            description = "Replaces the inspector's answers for the items supplied, upserted by item code.")
    public ResponseEntity<ApiResponse<List<ChecklistEntryResponse>>> saveChecklist(
            @PathVariable UUID id,
            @Valid @RequestBody ChecklistBulkUpsertRequest request) {
        var response = checklistService.upsert(id, request, SecurityUtils.currentUserId());
        return ResponseEntity.ok(ApiResponse.success("Checklist saved", response));
    }

    @GetMapping("/{id}/findings")
    @Operation(summary = "List findings", description = "Inspector-authored findings, distinct from AI-derived violations.")
    public ResponseEntity<ApiResponse<List<FindingResponse>>> listFindings(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(findingService.list(id)));
    }

    @PostMapping("/{id}/findings")
    @Operation(summary = "Add a finding")
    public ResponseEntity<ApiResponse<FindingResponse>> addFinding(
            @PathVariable UUID id,
            @Valid @RequestBody FindingCreateRequest request) {
        FindingResponse response = findingService.create(id, request, SecurityUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Finding recorded", response));
    }

    @GetMapping("/{id}/officer-evidence")
    @Operation(summary = "List officer-captured evidence",
            description = "Photographs the inspector captured during the workflow - distinct from "
                    + "GET /{id}/evidence, which is the AI pipeline's per-violation regions.")
    public ResponseEntity<ApiResponse<List<InspectionEvidenceResponse>>> listOfficerEvidence(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(inspectionEvidenceService.list(id)));
    }

    @PostMapping(value = "/{id}/officer-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an officer-captured evidence photo")
    public ResponseEntity<ApiResponse<InspectionEvidenceResponse>> uploadOfficerEvidence(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String description) {
        InspectionEvidenceResponse response = inspectionEvidenceService.upload(
                id, file, label, description, SecurityUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence stored", response));
    }
}
