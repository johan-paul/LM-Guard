package com.lmguard.service;

import com.lmguard.config.properties.StorageProperties;
import com.lmguard.dto.inspection.InspectionEvidenceResponse;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.InspectionEvidence;
import com.lmguard.exception.ApiException;
import com.lmguard.exception.BadRequestException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.mapper.InspectionEvidenceMapper;
import com.lmguard.repository.InspectionEvidenceRepository;
import com.lmguard.repository.UserRepository;
import com.lmguard.security.SecurityUtils;
import com.lmguard.storage.FileStorageService;
import com.lmguard.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Officer-captured evidence photos from the six-step workflow - distinct from
 * {@link com.lmguard.entity.Evidence}, the AI-pipeline's per-violation bounding-box regions.
 * Reuses the same {@link FileStorageService} the package-image upload already uses, writing to
 * the {@code evidence} bucket that was declared but never actually used before this.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InspectionEvidenceService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif");

    private final InspectionEvidenceRepository inspectionEvidenceRepository;
    private final InspectionService inspectionService;
    private final FileStorageService fileStorageService;
    private final InspectionEvidenceMapper inspectionEvidenceMapper;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<InspectionEvidenceResponse> list(UUID inspectionId) {
        inspectionService.requireInspection(inspectionId);
        return inspectionEvidenceRepository.findByInspectionIdOrderByCapturedAtAsc(inspectionId).stream()
                .map(inspectionEvidenceMapper::toResponse)
                .toList();
    }

    @Transactional
    public InspectionEvidenceResponse upload(UUID inspectionId, MultipartFile file, String label,
                                             String description, UUID callerId) {
        Inspection inspection = inspectionService.requireInspection(inspectionId);
        requireAssignedInspectorOrAdmin(inspection, callerId);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.IMAGE_REQUIRED, "No image file was supplied");
        }
        String contentType = file.getContentType() == null ? null : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType == null || "application/octet-stream".equals(contentType)) {
            contentType = resolveContentTypeFromFilename(file.getOriginalFilename());
        }
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE,
                    "Unsupported image type '%s'. Allowed types: %s".formatted(contentType, ALLOWED_IMAGE_TYPES));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE, "Could not read the uploaded image");
        }

        StoredFile stored = fileStorageService.upload(StorageProperties.EVIDENCE, bytes, file.getOriginalFilename(), contentType);

        InspectionEvidence saved = inspectionEvidenceRepository.save(InspectionEvidence.builder()
                .inspection(inspection)
                .capturedBy(userRepository.findById(callerId).orElse(null))
                .imageUrl(stored.url())
                .imagePath(stored.path())
                .label(label)
                .description(description)
                .build());

        log.info("Evidence photo stored for inspection {} at {} ({} bytes)", inspectionId, stored.path(), bytes.length);
        return inspectionEvidenceMapper.toResponse(saved);
    }

    private void requireAssignedInspectorOrAdmin(Inspection inspection, UUID callerId) {
        boolean isAssignedInspector = inspection.getInspector() != null
                && inspection.getInspector().getId().equals(callerId);
        if (!isAssignedInspector && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the assigned inspector can upload evidence");
        }
    }

    private String resolveContentTypeFromFilename(String filename) {
        if (filename == null) return "image/jpeg";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".heic")) return "image/heic";
        if (lower.endsWith(".heif")) return "image/heif";
        return "image/jpeg";
    }
}
