package com.lmguard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lmguard.dto.inspection.BoundingBoxResponse;
import com.lmguard.dto.inspection.EvidenceResponse;
import com.lmguard.dto.inspection.ExtractedFieldResponse;
import com.lmguard.dto.inspection.ImageUploadResponse;
import com.lmguard.dto.inspection.InspectionCreateRequest;
import com.lmguard.dto.inspection.InspectionResponse;
import com.lmguard.dto.inspection.RiskBreakdownResponse;
import com.lmguard.dto.inspection.ViolationResponse;
import com.lmguard.dto.product.ProductResponse;
import com.lmguard.entity.enums.ComplianceStatus;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.ProductField;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.Role;
import com.lmguard.entity.enums.Severity;
import com.lmguard.entity.enums.ViolationStatus;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.GlobalExceptionHandler;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.EvidenceMapper;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.mapper.ProductMapper;
import com.lmguard.mapper.ViolationMapper;
import com.lmguard.security.UserPrincipal;
import com.lmguard.service.ChecklistService;
import com.lmguard.service.FindingService;
import com.lmguard.service.InspectionEvidenceService;
import com.lmguard.service.InspectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the HTTP contract of the inspection endpoints: status codes, the response envelope,
 * and the shape the React client depends on.
 *
 * <p>Uses standalone MockMvc rather than a full application context so the test stays fast and
 * exercises only the web layer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inspection REST API")
class InspectionControllerTest {

    private static final UUID INSPECTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INSPECTOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VIOLATION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private InspectionService inspectionService;

    @Mock
    private ChecklistService checklistService;

    @Mock
    private FindingService findingService;

    @Mock
    private InspectionEvidenceService inspectionEvidenceService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        InspectionController controller = new InspectionController(
                inspectionService,
                checklistService,
                findingService,
                inspectionEvidenceService,
                new InspectionMapper(new ProductMapper(), new ViolationMapper(new EvidenceMapper())),
                new EvidenceMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();

        authenticateAsInspector();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsInspector() {
        UserPrincipal principal = new UserPrincipal(
                INSPECTOR_ID, "Test Inspector", "inspector@example.test", "hash", Role.INSPECTOR, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/inspections returns 201 in the standard envelope")
    void createReturnsCreated() throws Exception {
        when(inspectionService.create(any(), eq(INSPECTOR_ID))).thenReturn(pendingInspection());

        mockMvc.perform(post("/api/inspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InspectionCreateRequest(
                                null, "Classic Salted Chips", "ABC Foods", "PACKAGED_FOOD", null, "aisle 4",
                                null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Inspection created"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.inspectionId").value(INSPECTION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/inspections/{id}/analyze returns the full result")
    void analyzeReturnsFullResult() throws Exception {
        when(inspectionService.analyze(INSPECTION_ID)).thenReturn(completedInspection());

        mockMvc.perform(post("/api/inspections/{id}/analyze", INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.aiSuggestedStatus").value("NON_COMPLIANT"))
                .andExpect(jsonPath("$.data.riskScore").value(72))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.rulesetVersion").value("DEMO-2026.1"))
                .andExpect(jsonPath("$.data.fields[0].name").value(ProductField.MRP))
                .andExpect(jsonPath("$.data.fields[2].status").value("NON_COMPLIANT"))
                .andExpect(jsonPath("$.data.violations[0].ruleCode").value("DEMO-RULE-001"))
                .andExpect(jsonPath("$.data.violations[0].evidence.x").value(120))
                .andExpect(jsonPath("$.data.violations[0].evidence.width").value(200))
                .andExpect(jsonPath("$.data.risk.totalScore").value(72));
    }

    @Test
    @DisplayName("GET /api/inspections/{id} returns 404 with a stable error code")
    void notFoundUsesErrorCode() throws Exception {
        when(inspectionService.getById(INSPECTION_ID))
                .thenThrow(ResourceNotFoundException.of(ErrorCode.INSPECTION_NOT_FOUND, INSPECTION_ID));

        mockMvc.perform(get("/api/inspections/{id}", INSPECTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INSPECTION_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/inspections/{id}/image accepts a multipart upload")
    void uploadImage() throws Exception {
        when(inspectionService.uploadImage(eq(INSPECTION_ID), any()))
                .thenReturn(new ImageUploadResponse(INSPECTION_ID,
                        "http://localhost:8080/files/package-images/2026/09/04/x.jpg",
                        "2026/09/04/x.jpg", 1024L, "image/jpeg"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "pack.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/inspections/{id}/image", INSPECTION_ID).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").exists())
                .andExpect(jsonPath("$.data.sizeBytes").value(1024));
    }

    @Test
    @DisplayName("GET /api/inspections/{id}/evidence returns the recorded regions")
    void evidenceForInspection() throws Exception {
        when(inspectionService.evidenceForInspection(INSPECTION_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/inspections/{id}/evidence", INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("a validation failure names the offending field")
    void validationFailureNamesField() throws Exception {
        // productName exceeds its maximum length.
        String tooLong = "x".repeat(300);
        mockMvc.perform(post("/api/inspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InspectionCreateRequest(
                                null, tooLong, null, null, null, null, null, null, null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.productName").exists());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private ProductResponse product() {
        return new ProductResponse(PRODUCT_ID, "Classic Salted Chips", "ABC Foods",
                "PACKAGED_FOOD", "8901234567890", Instant.parse("2026-09-04T10:00:00Z"),
                Instant.parse("2026-09-04T10:00:00Z"), null);
    }

    private InspectionResponse pendingInspection() {
        return new InspectionResponse(INSPECTION_ID, InspectionStatus.PENDING, null, null, null,
                null, null, null, List.of(), null, product(), INSPECTOR_ID, "Test Inspector", null, null,
                null, null, null, null, null, "aisle 4", null,
                List.of(), List.of(), null, Instant.parse("2026-09-04T12:00:00Z"), null, null);
    }

    /** Mirrors the documented success shape so the frontend contract is pinned by a test. */
    private InspectionResponse completedInspection() {
        EvidenceResponse evidence = new EvidenceResponse(
                UUID.randomUUID(), VIOLATION_ID, INSPECTION_ID,
                "http://localhost:8080/files/package-images/2026/09/04/x.jpg",
                120, 340, 200, 80, new BigDecimal("0.9100"),
                "No region of the package image was found to contain 'CONSUMER_CARE'.",
                Instant.parse("2026-09-04T12:00:05Z"));

        ViolationResponse violation = new ViolationResponse(
                VIOLATION_ID, "DEMO-RULE-001", ProductField.CONSUMER_CARE,
                "Required declaration not detected", "Declare a consumer care contact",
                ViolationStatus.NON_COMPLIANT, Severity.MAJOR, new BigDecimal("0.9100"),
                null, evidence, List.of(evidence));

        List<ExtractedFieldResponse> fields = List.of(
                new ExtractedFieldResponse(UUID.randomUUID(), ProductField.MRP, "99",
                        new BigDecimal("0.9700"), ComplianceStatus.COMPLIANT,
                        BoundingBoxResponse.of(64, 210, 150, 54)),
                new ExtractedFieldResponse(UUID.randomUUID(), ProductField.NET_QUANTITY, "500 g",
                        new BigDecimal("0.9500"), ComplianceStatus.COMPLIANT,
                        BoundingBoxResponse.of(64, 280, 180, 52)),
                new ExtractedFieldResponse(UUID.randomUUID(), ProductField.CONSUMER_CARE, null,
                        new BigDecimal("0.9100"), ComplianceStatus.NON_COMPLIANT, null));

        RiskBreakdownResponse risk = new RiskBreakdownResponse(
                30, 20, 0, 10, 15, 72, RiskLevel.HIGH, "Score 72 of 100 (HIGH).");

        return new InspectionResponse(INSPECTION_ID, InspectionStatus.IN_PROGRESS, InspectionStatus.NON_COMPLIANT,
                new BigDecimal("0.9400"), 72, RiskLevel.HIGH, "DEMO-2026.1", "MOCK", List.of(),
                "http://localhost:8080/files/package-images/2026/09/04/x.jpg",
                product(), INSPECTOR_ID, "Test Inspector", null, null,
                null, null, null, null, null, null, null,
                fields, List.of(violation), risk,
                Instant.parse("2026-09-04T12:00:00Z"), Instant.parse("2026-09-04T12:00:05Z"), null);
    }
}
