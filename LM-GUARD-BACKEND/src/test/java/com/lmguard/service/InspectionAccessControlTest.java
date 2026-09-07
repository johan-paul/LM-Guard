package com.lmguard.service;

import com.lmguard.entity.Inspection;
import com.lmguard.entity.User;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.Role;
import com.lmguard.evidence.EvidenceService;
import com.lmguard.exception.ApiException;
import com.lmguard.mapper.AssignmentHistoryMapper;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.repository.AssignmentHistoryRepository;
import com.lmguard.repository.ChecklistEntryRepository;
import com.lmguard.repository.EvidenceRepository;
import com.lmguard.repository.ExtractedFieldRepository;
import com.lmguard.repository.InspectionEvidenceRepository;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.UserRepository;
import com.lmguard.repository.ViolationRepository;
import com.lmguard.security.UserPrincipal;
import com.lmguard.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may read an inspection - either a single record ({@code GET /{id}}) or the list
 * ({@code GET /inspections}). An admin sees everything; an inspector only their own, and that
 * restriction is enforced server-side rather than trusted to whatever filter the client sends,
 * so a modified or buggy client can't be used to browse another officer's caseload.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inspection read access control")
class InspectionAccessControlTest {

    private static final UUID INSPECTION_ID = UUID.randomUUID();
    private static final UUID INSPECTOR_ID = UUID.randomUUID();
    private static final UUID OTHER_INSPECTOR_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private InspectionRepository inspectionRepository;
    @Mock private ExtractedFieldRepository extractedFieldRepository;
    @Mock private ViolationRepository violationRepository;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private UserRepository userRepository;
    @Mock private AssignmentHistoryRepository assignmentHistoryRepository;
    @Mock private ChecklistEntryRepository checklistEntryRepository;
    @Mock private EvidenceRepository evidenceRepository;
    @Mock private InspectionEvidenceRepository inspectionEvidenceRepository;
    @Mock private ProductService productService;
    @Mock private ZoneService zoneService;
    @Mock private FileStorageService fileStorageService;
    @Mock private EvidenceService evidenceService;
    @Mock private InspectionAnalysisService analysisService;
    @Mock private InspectionStatusWriter statusWriter;
    @Mock private InspectionMapper inspectionMapper;
    @Mock private AssignmentHistoryMapper assignmentHistoryMapper;

    private InspectionService service;

    @BeforeEach
    void setUp() {
        service = new InspectionService(inspectionRepository, extractedFieldRepository, violationRepository,
                riskScoreRepository, userRepository, assignmentHistoryRepository, checklistEntryRepository,
                evidenceRepository, inspectionEvidenceRepository,
                productService, zoneService, fileStorageService, evidenceService, analysisService, statusWriter,
                inspectionMapper, assignmentHistoryMapper);

        when(extractedFieldRepository.findByInspectionIdOrderByFieldNameAsc(any())).thenReturn(List.of());
        when(violationRepository.findByInspectionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(riskScoreRepository.findByInspectionId(any())).thenReturn(Optional.empty());
        when(inspectionMapper.toResponse(any(), any(), any(), any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, Role role) {
        UserPrincipal principal = new UserPrincipal(userId, "Test User", "test@example.test", "hash", role, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Inspection inspectionAssignedTo(UUID inspectorId) {
        User inspector = User.builder().name("Inspector").email("inspector@example.test")
                .passwordHash("x").role(Role.INSPECTOR).enabled(true).build();
        inspector.setId(inspectorId);
        Inspection inspection = Inspection.builder().inspector(inspector).status(InspectionStatus.IN_PROGRESS).build();
        inspection.setId(INSPECTION_ID);
        return inspection;
    }

    @Test
    @DisplayName("getById lets the assigned inspector read their own inspection")
    void getByIdAllowsAssignedInspector() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspectionAssignedTo(INSPECTOR_ID)));

        service.getById(INSPECTION_ID);
        // No exception - the mocked mapper returning null is fine, only the access check matters here.
    }

    @Test
    @DisplayName("getById refuses an inspector reading a case assigned to someone else")
    void getByIdRefusesOtherInspector() {
        authenticateAs(OTHER_INSPECTOR_ID, Role.INSPECTOR);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspectionAssignedTo(INSPECTOR_ID)));

        assertThatThrownBy(() -> service.getById(INSPECTION_ID)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("getById lets an admin read any inspection")
    void getByIdAllowsAdmin() {
        authenticateAs(ADMIN_ID, Role.ADMIN);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspectionAssignedTo(INSPECTOR_ID)));

        service.getById(INSPECTION_ID);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("search forces a non-admin caller's inspectorId to their own id, ignoring any other value requested")
    void searchForcesInspectorToOwnRecords() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        Page<Inspection> empty = new PageImpl<>(List.of());
        when(inspectionRepository.searchDetailed(any(), any(), any(), any(), any())).thenReturn(empty);

        service.search(null, null, OTHER_INSPECTOR_ID, null, PageRequest.of(0, 20));

        ArgumentCaptor<UUID> inspectorIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(inspectionRepository).searchDetailed(isNull(), isNull(), inspectorIdCaptor.capture(), isNull(), any());
        assertThat(inspectorIdCaptor.getValue()).isEqualTo(INSPECTOR_ID);
    }

    @Test
    @DisplayName("search honours an admin's requested inspectorId filter (or none, seeing everyone)")
    void searchAllowsAdminRequestedFilter() {
        authenticateAs(ADMIN_ID, Role.ADMIN);
        Page<Inspection> empty = new PageImpl<>(List.of());
        when(inspectionRepository.searchDetailed(any(), any(), any(), any(), any())).thenReturn(empty);

        service.search(null, null, OTHER_INSPECTOR_ID, null, PageRequest.of(0, 20));

        verify(inspectionRepository).searchDetailed(isNull(), isNull(), eq(OTHER_INSPECTOR_ID), isNull(), any());
    }
}
