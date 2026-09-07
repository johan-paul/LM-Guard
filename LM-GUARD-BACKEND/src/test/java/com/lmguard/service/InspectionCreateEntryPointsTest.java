package com.lmguard.service;

import com.lmguard.dto.inspection.InspectionCreateRequest;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.User;
import com.lmguard.entity.Zone;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LM-GUARD supports two entry points into the same inspection model and six-step workflow: an
 * inspector opening their own inspection directly, or an admin opening one and assigning it to a
 * specific inspector. Which one applies is decided solely by whether {@code inspectorId} is
 * present - never by role alone, and {@code zoneId} must not accidentally gate on the same
 * admin-only check (an inspector tagging their own inspection's zone is not an assignment).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inspection creation - both entry points")
class InspectionCreateEntryPointsTest {

    private static final UUID INSPECTOR_ID = UUID.randomUUID();
    private static final UUID OTHER_INSPECTOR_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID ZONE_ID = UUID.randomUUID();

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

        when(inspectionMapper.toResponse(any(), any(), any(), any())).thenReturn(null);
        when(extractedFieldRepository.findByInspectionIdOrderByFieldNameAsc(any())).thenReturn(List.of());
        when(violationRepository.findByInspectionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(riskScoreRepository.findByInspectionId(any())).thenReturn(Optional.empty());
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(inv -> {
            Inspection inspection = inv.getArgument(0);
            if (inspection.getId() == null) {
                inspection.setId(UUID.randomUUID());
            }
            return inspection;
        });
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

    private User userWithId(UUID id, Role role) {
        User user = User.builder().name("User").email(id + "@example.test")
                .passwordHash("x").role(role).enabled(true).build();
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("an inspector can open their own inspection directly, with no admin involvement")
    void inspectorOpensOwnInspectionDirectly() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        User inspector = userWithId(INSPECTOR_ID, Role.INSPECTOR);
        when(userRepository.findById(INSPECTOR_ID)).thenReturn(Optional.of(inspector));

        InspectionCreateRequest request = new InspectionCreateRequest(
                null, null, null, null, null, null, null, null,
                "Corner Store", "12 MG Road", null, null, null);

        service.create(request, INSPECTOR_ID);

        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionRepository).save(captor.capture());
        Inspection saved = captor.getValue();

        assertThat(saved.getInspector().getId()).isEqualTo(INSPECTOR_ID);
        assertThat(saved.getProduct()).as("product identification is a later workflow step").isNull();
        assertThat(saved.getAssignedBy()).as("no admin assigned this - the inspector opened it themselves").isNull();
        verify(assignmentHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("an inspector may tag their own inspection with a zone without triggering the admin-only check")
    void inspectorCanSetOwnZone() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        when(userRepository.findById(INSPECTOR_ID)).thenReturn(Optional.of(userWithId(INSPECTOR_ID, Role.INSPECTOR)));
        Zone zone = Zone.builder().name("Coimbatore North").build();
        zone.setId(ZONE_ID);
        when(zoneService.requireById(ZONE_ID)).thenReturn(zone);

        InspectionCreateRequest request = new InspectionCreateRequest(
                null, null, null, null, null, null, null, ZONE_ID,
                "Corner Store", null, null, null, null);

        // Must not throw FORBIDDEN merely because zoneId is present.
        service.create(request, INSPECTOR_ID);

        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionRepository).save(captor.capture());
        assertThat(captor.getValue().getZone()).isEqualTo(zone);
        assertThat(captor.getValue().getAssignedBy()).isNull();
        verify(assignmentHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("a plain inspector cannot assign an inspection to a different inspector")
    void inspectorCannotAssignToSomeoneElse() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        when(userRepository.findById(INSPECTOR_ID)).thenReturn(Optional.of(userWithId(INSPECTOR_ID, Role.INSPECTOR)));

        InspectionCreateRequest request = new InspectionCreateRequest(
                null, null, null, null, null, null, OTHER_INSPECTOR_ID, null,
                null, null, null, null, null);

        assertThatThrownBy(() -> service.create(request, INSPECTOR_ID))
                .isInstanceOf(ApiException.class);
        verify(inspectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("an admin can open a case and assign it to a specific inspector, recording the assignment")
    void adminAssignsToInspector() {
        authenticateAs(ADMIN_ID, Role.ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(userWithId(ADMIN_ID, Role.ADMIN)));
        when(userRepository.findById(OTHER_INSPECTOR_ID)).thenReturn(Optional.of(userWithId(OTHER_INSPECTOR_ID, Role.INSPECTOR)));
        Zone zone = Zone.builder().name("Coimbatore South").build();
        zone.setId(ZONE_ID);
        when(zoneService.requireById(ZONE_ID)).thenReturn(zone);

        InspectionCreateRequest request = new InspectionCreateRequest(
                null, null, null, null, null, null, OTHER_INSPECTOR_ID, ZONE_ID,
                "Retail Outlet", null, null, null, null);

        service.create(request, ADMIN_ID);

        ArgumentCaptor<Inspection> captor = ArgumentCaptor.forClass(Inspection.class);
        verify(inspectionRepository).save(captor.capture());
        Inspection saved = captor.getValue();

        assertThat(saved.getInspector().getId()).isEqualTo(OTHER_INSPECTOR_ID);
        assertThat(saved.getAssignedBy().getId()).isEqualTo(ADMIN_ID);
        assertThat(saved.getAssignedAt()).isNotNull();
        verify(assignmentHistoryRepository).save(any());
    }
}
