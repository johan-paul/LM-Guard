package com.lmguard.service;

import com.lmguard.config.properties.StorageProperties;
import com.lmguard.dto.inspection.AssignmentHistoryResponse;
import com.lmguard.dto.inspection.ImageUploadResponse;
import com.lmguard.dto.inspection.InspectionAssignmentRequest;
import com.lmguard.dto.inspection.InspectionCreateRequest;
import com.lmguard.dto.inspection.InspectionResponse;
import com.lmguard.dto.product.ProductCreateRequest;
import com.lmguard.entity.AssignmentHistory;
import com.lmguard.entity.Evidence;
import com.lmguard.entity.ExtractedField;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.Product;
import com.lmguard.entity.RiskScore;
import com.lmguard.entity.User;
import com.lmguard.entity.Violation;
import com.lmguard.entity.Zone;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.evidence.EvidenceService;
import com.lmguard.exception.ApiException;
import com.lmguard.exception.BadRequestException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.AssignmentHistoryMapper;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.repository.AssignmentHistoryRepository;
import com.lmguard.repository.ExtractedFieldRepository;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.UserRepository;
import com.lmguard.repository.ViolationRepository;
import com.lmguard.security.SecurityUtils;
import com.lmguard.storage.FileStorageService;
import com.lmguard.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The API-facing inspection service: create, upload, analyse, read.
 *
 * <p>{@link #analyze(UUID)} is deliberately <strong>not</strong> transactional. It brackets the
 * transactional pipeline in {@link InspectionAnalysisService} with status writes from
 * {@link InspectionStatusWriter}, so a pipeline failure rolls back cleanly and still leaves a
 * durable {@code FAILED} record with a reason - rather than an inspection stuck in
 * {@code PROCESSING} with no explanation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InspectionService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif");

    private final InspectionRepository inspectionRepository;
    private final ExtractedFieldRepository extractedFieldRepository;
    private final ViolationRepository violationRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final UserRepository userRepository;
    private final AssignmentHistoryRepository assignmentHistoryRepository;
    private final com.lmguard.repository.ChecklistEntryRepository checklistEntryRepository;
    private final com.lmguard.repository.EvidenceRepository evidenceRepository;
    private final com.lmguard.repository.InspectionEvidenceRepository inspectionEvidenceRepository;

    private final ProductService productService;
    private final ZoneService zoneService;
    private final FileStorageService fileStorageService;
    private final EvidenceService evidenceService;
    private final InspectionAnalysisService analysisService;
    private final InspectionStatusWriter statusWriter;
    private final InspectionMapper inspectionMapper;
    private final AssignmentHistoryMapper assignmentHistoryMapper;

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Transactional
    public InspectionResponse create(InspectionCreateRequest request, UUID callerId) {
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.USER_NOT_FOUND, callerId));

        // Naming a specific inspectorId is what makes this an admin assignment (as opposed to
        // an inspector opening their own inspection, which never sets inspectorId) - it can't be
        // spoofed by a non-admin passing someone else's id in the request body. zoneId carries
        // no such restriction: it is just informational either way.
        if (request.requestsAssignment() && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Only an administrator can assign an inspection to another inspector");
        }

        User inspector = request.inspectorId() == null
                ? caller
                : userRepository.findById(request.inspectorId())
                        .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.USER_NOT_FOUND, request.inspectorId()));

        Zone zone = request.zoneId() == null ? null : zoneService.requireById(request.zoneId());
        Product product = resolveProduct(request);

        Inspection.InspectionBuilder builder = Inspection.builder()
                .product(product)
                .inspector(inspector)
                .zone(zone)
                .status(InspectionStatus.PENDING)
                .notes(request.notes())
                .establishment(trimToNull(request.establishment()))
                .address(trimToNull(request.address()))
                .inspectionType(request.inspectionType())
                .priority(request.priority())
                .dueDate(request.dueDate());

        if (request.requestsAssignment()) {
            builder.assignedBy(caller).assignedAt(java.time.Instant.now());
        }

        Inspection inspection = inspectionRepository.save(builder.build());

        if (request.requestsAssignment()) {
            assignmentHistoryRepository.save(AssignmentHistory.builder()
                    .inspection(inspection)
                    .fromInspector(null)
                    .toInspector(inspector)
                    .fromZone(null)
                    .toZone(zone)
                    .assignedBy(caller)
                    .reason("Assigned on creation")
                    .build());
        }

        log.info("Opened inspection {} for {} by {}{}",
                inspection.getId(), product == null ? "a case with no product yet" : "product " + product.getId(),
                inspector.getEmail(), request.requestsAssignment() ? " (assigned by " + caller.getEmail() + ")" : "");

        return buildResponse(inspection);
    }

    /**
     * Identifies/attaches a product to an inspection that was opened without one - the
     * product-identification step of the six-step workflow.
     */
    @Transactional
    public InspectionResponse identifyProduct(UUID inspectionId, com.lmguard.dto.inspection.InspectionProductRequest request) {
        Inspection inspection = requireInspection(inspectionId);

        Product product = request.createsProductInline()
                ? resolveInlineProduct(request)
                : productService.requireById(request.productId());

        inspection.setProduct(product);
        inspectionRepository.save(inspection);

        log.info("Inspection {} identified product {}", inspectionId, product.getId());
        return buildResponse(inspection);
    }

    private Product resolveInlineProduct(com.lmguard.dto.inspection.InspectionProductRequest request) {
        if (request.productName() == null || request.productName().isBlank()) {
            throw new BadRequestException("Supply either productId, or productName to register a new product inline");
        }
        UUID createdId = productService.create(new ProductCreateRequest(
                request.productName(), request.brand(), request.category(), request.barcode())).id();
        return productService.requireById(createdId);
    }

    /**
     * The inspector's final, independent decision. AI/rule-engine suggestions
     * ({@link Inspection#getAiSuggestedStatus()}) never reach this method - only an explicit
     * call here can set the inspection's authoritative outcome.
     */
    @Transactional
    public InspectionResponse submit(UUID inspectionId, com.lmguard.dto.inspection.InspectionSubmitRequest request,
                                     UUID callerId) {
        Inspection inspection = requireInspection(inspectionId);

        if (!SecurityUtils.isAdmin() && !inspection.getInspector().getId().equals(callerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the assigned inspector can submit this inspection");
        }
        if (!request.finalDecision().isVerdict()) {
            throw new BadRequestException(ErrorCode.INVALID_FINAL_DECISION,
                    "finalDecision must be COMPLIANT, NON_COMPLIANT or INCONCLUSIVE, not " + request.finalDecision());
        }
        long pendingChecklistItems = checklistEntryRepository.countPendingByInspectionId(inspectionId);
        if (pendingChecklistItems > 0) {
            throw new BadRequestException(ErrorCode.CHECKLIST_INCOMPLETE,
                    "%d checklist item(s) are still unanswered".formatted(pendingChecklistItems));
        }

        // Evidence-first: a NON_COMPLIANT verdict is an assertion that something is wrong with
        // the package, and that assertion must point at something - either the rule engine's
        // own AI-drawn evidence (the normal path: every violation gets one) or, failing that, a
        // photo the officer captured by hand. COMPLIANT/INCONCLUSIVE aren't gated the same way -
        // the package photo captured before analysis (already mandatory - see
        // InspectionAnalysisService.run()'s IMAGE_REQUIRED check) is evidence enough for "nothing
        // wrong was found"; it's specifically the accusation that needs backing.
        if (request.finalDecision() == InspectionStatus.NON_COMPLIANT) {
            boolean hasEvidence = !evidenceRepository.findByInspectionIdOrderByCreatedAtAsc(inspectionId).isEmpty()
                    || !inspectionEvidenceRepository.findByInspectionIdOrderByCapturedAtAsc(inspectionId).isEmpty();
            if (!hasEvidence) {
                throw new BadRequestException(ErrorCode.EVIDENCE_REQUIRED, ErrorCode.EVIDENCE_REQUIRED.getDefaultMessage());
            }
        }

        inspection.setStatus(request.finalDecision());
        if (request.notes() != null && !request.notes().isBlank()) {
            inspection.setNotes(request.notes());
        }
        inspection.setCompletedAt(java.time.Instant.now());
        inspectionRepository.save(inspection);

        log.info("Inspection {} submitted by {}: final decision {}", inspectionId, callerId, request.finalDecision());
        return buildResponse(inspection);
    }

    /**
     * Saves the inspector's working notes for an in-progress inspection, independent of
     * {@link #submit}, so a draft that hasn't been submitted yet - and might not be, in this
     * same app session - doesn't lose them on the next {@code fetchInspection}.
     */
    @Transactional
    public InspectionResponse updateNotes(UUID inspectionId, com.lmguard.dto.inspection.InspectionNotesRequest request,
                                          UUID callerId) {
        Inspection inspection = requireInspection(inspectionId);

        if (!SecurityUtils.isAdmin() && !inspection.getInspector().getId().equals(callerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the assigned inspector can update this inspection's notes");
        }

        inspection.setNotes(trimToNull(request.notes()));
        inspectionRepository.save(inspection);
        return buildResponse(inspection);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Reassigns an existing inspection to a different inspector and/or zone. ADMIN only
     * (enforced at the controller); every call writes an {@link AssignmentHistory} row so the
     * assignment trail is never lost, even across repeated reassignments.
     */
    @Transactional
    public InspectionResponse reassign(UUID inspectionId, InspectionAssignmentRequest request, UUID adminId) {
        Inspection inspection = requireInspection(inspectionId);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.USER_NOT_FOUND, adminId));
        User newInspector = userRepository.findById(request.inspectorId())
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.USER_NOT_FOUND, request.inspectorId()));

        User previousInspector = inspection.getInspector();
        Zone previousZone = inspection.getZone();
        Zone newZone = request.zoneId() == null ? previousZone : zoneService.requireById(request.zoneId());

        inspection.setInspector(newInspector);
        inspection.setZone(newZone);
        inspection.setAssignedBy(admin);
        inspection.setAssignedAt(java.time.Instant.now());
        inspectionRepository.save(inspection);

        assignmentHistoryRepository.save(AssignmentHistory.builder()
                .inspection(inspection)
                .fromInspector(previousInspector)
                .toInspector(newInspector)
                .fromZone(previousZone)
                .toZone(newZone)
                .assignedBy(admin)
                .reason(request.reason())
                .build());

        log.info("Inspection {} reassigned from {} to {} by {}", inspectionId,
                previousInspector == null ? "none" : previousInspector.getEmail(),
                newInspector.getEmail(), admin.getEmail());

        return buildResponse(inspection);
    }

    @Transactional(readOnly = true)
    public List<AssignmentHistoryResponse> assignmentHistory(UUID inspectionId) {
        requireInspectionExists(inspectionId);
        return assignmentHistoryRepository.findByInspectionIdOrderByCreatedAtDesc(inspectionId).stream()
                .map(assignmentHistoryMapper::toResponse)
                .toList();
    }

    /**
     * Uses the referenced product, registers one inline for an unfamiliar package, or - for an
     * admin-opened case only - leaves the product unset until the assigned inspector identifies
     * one via {@code PATCH .../product}.
     */
    private Product resolveProduct(InspectionCreateRequest request) {
        if (!request.createsProductInline()) {
            return productService.requireById(request.productId());
        }
        if (request.hasNoProduct()) {
            // Valid for either entry point: product identification is the six-step workflow's
            // own step, not necessarily known at creation time.
            return null;
        }
        UUID createdId = productService.create(new ProductCreateRequest(
                request.productName(), request.brand(), request.category(), request.barcode())).id();
        return productService.requireById(createdId);
    }

    // ------------------------------------------------------------------
    // Upload image
    // ------------------------------------------------------------------

    @Transactional
    public ImageUploadResponse uploadImage(UUID inspectionId, MultipartFile file) {
        Inspection inspection = requireInspection(inspectionId);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.IMAGE_REQUIRED, "No image file was supplied");
        }
        String contentType = file.getContentType() == null
                ? null
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE,
                    "Unsupported image type '%s'. Allowed types: %s".formatted(contentType, ALLOWED_IMAGE_TYPES));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE, "Could not read the uploaded image");
        }

        String previousPath = inspection.getImagePath();

        StoredFile stored = fileStorageService.upload(
                StorageProperties.PACKAGE_IMAGES, bytes, file.getOriginalFilename(), contentType);

        inspection.setImageUrl(stored.url());
        inspection.setImagePath(stored.path());
        inspectionRepository.save(inspection);

        // Re-uploading would otherwise orphan the previous image in the bucket.
        if (previousPath != null && !previousPath.equals(stored.path())) {
            fileStorageService.delete(StorageProperties.PACKAGE_IMAGES, previousPath);
        }

        log.info("Stored image for inspection {} at {} ({} bytes)", inspectionId, stored.path(), bytes.length);
        return new ImageUploadResponse(inspectionId, stored.url(), stored.path(), bytes.length, contentType);
    }

    // ------------------------------------------------------------------
    // Analyse
    // ------------------------------------------------------------------

    /**
     * Runs the full pipeline. Not transactional by design - see the class comment.
     *
     * @throws com.lmguard.exception.ApiException propagated from the pipeline after the
     *         inspection has been durably marked FAILED
     */
    public InspectionResponse analyze(UUID inspectionId) {
        // Fails fast with 404 before any status is written.
        requireInspectionExists(inspectionId);

        statusWriter.markProcessing(inspectionId);
        try {
            return analysisService.run(inspectionId);
        } catch (RuntimeException ex) {
            // The pipeline transaction has already rolled back, so this write is safe and durable.
            statusWriter.markFailed(inspectionId, ex.getMessage());
            throw ex;
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public InspectionResponse getById(UUID inspectionId) {
        Inspection inspection = requireInspection(inspectionId);
        requireViewAccess(inspection);
        return buildResponse(inspection);
    }

    /** ADMIN sees every inspection; an inspector only their own - the same "assigned inspector or
     * admin" rule already enforced on checklist/findings/evidence/notes, applied here so an
     * inspector can't read another officer's case by id even though ids aren't guessable in
     * practice. */
    private void requireViewAccess(Inspection inspection) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        boolean isAssignedInspector = inspection.getInspector() != null
                && inspection.getInspector().getId().equals(SecurityUtils.currentUserId());
        if (!isAssignedInspector) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You are not authorised to view this inspection");
        }
    }

    /** A non-admin caller can only ever list their own inspections - {@code inspectorId} is
     * forced to their own id regardless of what was requested, the same protection {@link
     * #getById} gives a single record. Only ADMIN may filter by (or omit, seeing everyone's)
     * an arbitrary inspector. */
    @Transactional(readOnly = true)
    public Page<Inspection> search(InspectionStatus status, UUID productId, UUID inspectorId, UUID zoneId,
                                   Pageable pageable) {
        UUID effectiveInspectorId = SecurityUtils.isAdmin() ? inspectorId : SecurityUtils.currentUserId();
        return inspectionRepository.searchDetailed(status, productId, effectiveInspectorId, zoneId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Evidence> evidenceForInspection(UUID inspectionId) {
        requireInspectionExists(inspectionId);
        return evidenceService.forInspection(inspectionId);
    }

    @Transactional(readOnly = true)
    public List<Evidence> evidenceForViolation(UUID violationId) {
        violationRepository.findById(violationId)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.VIOLATION_NOT_FOUND, violationId));
        return evidenceService.forViolation(violationId);
    }

    @Transactional(readOnly = true)
    public Inspection requireInspection(UUID inspectionId) {
        return inspectionRepository.findDetailedById(inspectionId)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.INSPECTION_NOT_FOUND, inspectionId));
    }

    private void requireInspectionExists(UUID inspectionId) {
        if (!inspectionRepository.existsById(inspectionId)) {
            throw ResourceNotFoundException.of(ErrorCode.INSPECTION_NOT_FOUND, inspectionId);
        }
    }

    private InspectionResponse buildResponse(Inspection inspection) {
        List<ExtractedField> fields =
                extractedFieldRepository.findByInspectionIdOrderByFieldNameAsc(inspection.getId());
        List<Violation> violations =
                violationRepository.findByInspectionIdOrderByCreatedAtAsc(inspection.getId());
        RiskScore riskScore = riskScoreRepository.findByInspectionId(inspection.getId()).orElse(null);
        return inspectionMapper.toResponse(inspection, fields, violations, riskScore);
    }
}
