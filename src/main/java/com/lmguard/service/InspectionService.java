package com.lmguard.service;

import com.lmguard.config.properties.StorageProperties;
import com.lmguard.dto.inspection.ImageUploadResponse;
import com.lmguard.dto.inspection.InspectionCreateRequest;
import com.lmguard.dto.inspection.InspectionResponse;
import com.lmguard.dto.product.ProductCreateRequest;
import com.lmguard.entity.Evidence;
import com.lmguard.entity.ExtractedField;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.Product;
import com.lmguard.entity.RiskScore;
import com.lmguard.entity.User;
import com.lmguard.entity.Violation;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.evidence.EvidenceService;
import com.lmguard.exception.BadRequestException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.repository.ExtractedFieldRepository;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.UserRepository;
import com.lmguard.repository.ViolationRepository;
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

    private final ProductService productService;
    private final FileStorageService fileStorageService;
    private final EvidenceService evidenceService;
    private final InspectionAnalysisService analysisService;
    private final InspectionStatusWriter statusWriter;
    private final InspectionMapper inspectionMapper;

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Transactional
    public InspectionResponse create(InspectionCreateRequest request, UUID inspectorId) {
        User inspector = userRepository.findById(inspectorId)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.USER_NOT_FOUND, inspectorId));

        Product product = resolveProduct(request);

        Inspection inspection = inspectionRepository.save(Inspection.builder()
                .product(product)
                .inspector(inspector)
                .status(InspectionStatus.PENDING)
                .notes(request.notes())
                .build());

        log.info("Opened inspection {} for product {} by {}",
                inspection.getId(), product.getId(), inspector.getEmail());

        return buildResponse(inspection);
    }

    /** Uses the referenced product, or registers one inline for an unfamiliar package. */
    private Product resolveProduct(InspectionCreateRequest request) {
        if (!request.createsProductInline()) {
            return productService.requireById(request.productId());
        }
        if (request.productName() == null || request.productName().isBlank()) {
            throw new BadRequestException(
                    "Supply either productId, or productName to register a new product inline");
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
        return buildResponse(requireInspection(inspectionId));
    }

    @Transactional(readOnly = true)
    public Page<Inspection> search(InspectionStatus status, UUID productId, UUID inspectorId, Pageable pageable) {
        return inspectionRepository.searchDetailed(status, productId, inspectorId, pageable);
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
