package com.lmguard.service;

import com.lmguard.dto.inspection.InspectionSubmitRequest;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.User;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.Role;
import com.lmguard.evidence.EvidenceService;
import com.lmguard.exception.ApiException;
import com.lmguard.exception.BadRequestException;
import com.lmguard.mapper.AssignmentHistoryMapper;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.entity.Evidence;
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
import static org.mockito.Mockito.when;

/**
 * The inspector's final-decision submission - the one place the inspection's authoritative
 * status is allowed to change. Confirms the AI's advisory suggestion never substitutes for it,
 * that submission is blocked while checklist items remain unanswered, and that only the
 * assigned inspector (or an admin) may submit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inspection submission (final decision)")
class InspectionSubmitTest {

    private static final UUID INSPECTION_ID = UUID.randomUUID();
    private static final UUID INSPECTOR_ID = UUID.randomUUID();
    private static final UUID OTHER_INSPECTOR_ID = UUID.randomUUID();

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
        // Evidence-first guard default: most tests here submit COMPLIANT/other decisions that
        // aren't gated on evidence at all, but default to "evidence exists" so the ones that do
        // submit NON_COMPLIANT aren't tripped up by an unrelated assertion - the dedicated
        // evidence-guard tests below override this explicitly.
        when(evidenceRepository.findByInspectionIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(Evidence.builder().build()));
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
    @DisplayName("sets the authoritative status and completedAt from the inspector's own decision")
    void submitSetsStatus() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        Inspection inspection = inspectionAssignedTo(INSPECTOR_ID);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspection));
        when(checklistEntryRepository.countPendingByInspectionId(INSPECTION_ID)).thenReturn(0L);
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(INSPECTION_ID, new InspectionSubmitRequest(InspectionStatus.NON_COMPLIANT, "Confirmed on site"),
                INSPECTOR_ID);

        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.NON_COMPLIANT);
        assertThat(inspection.getCompletedAt()).isNotNull();
        assertThat(inspection.getNotes()).isEqualTo("Confirmed on site");
    }

    @Test
    @DisplayName("rejects submission while checklist items are still unanswered")
    void rejectsIncompleteChecklist() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        Inspection inspection = inspectionAssignedTo(INSPECTOR_ID);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspection));
        when(checklistEntryRepository.countPendingByInspectionId(INSPECTION_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.submit(INSPECTION_ID,
                new InspectionSubmitRequest(InspectionStatus.COMPLIANT, null), INSPECTOR_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("rejects a finalDecision that is not a compliance verdict")
    void rejectsNonVerdictDecision() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        Inspection inspection = inspectionAssignedTo(INSPECTOR_ID);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspection));

        assertThatThrownBy(() -> service.submit(INSPECTION_ID,
                new InspectionSubmitRequest(InspectionStatus.IN_PROGRESS, null), INSPECTOR_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("refuses submission by an inspector the inspection isn't assigned to")
    void refusesWrongInspector() {
        authenticateAs(OTHER_INSPECTOR_ID, Role.INSPECTOR);
        Inspection inspection = inspectionAssignedTo(INSPECTOR_ID);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspection));

        assertThatThrownBy(() -> service.submit(INSPECTION_ID,
                new InspectionSubmitRequest(InspectionStatus.COMPLIANT, null), OTHER_INSPECTOR_ID))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("updateNotes saves working notes independent of submission, so a draft save doesn't lose them")
    void updateNotesSavesNotes() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        Inspection inspection = inspectionAssignedTo(INSPECTOR_ID);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspection));
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateNotes(INSPECTION_ID,
                new com.lmguard.dto.inspection.InspectionNotesRequest("  Spoke to the store manager.  "), INSPECTOR_ID);

        assertThat(inspection.getNotes()).isEqualTo("Spoke to the store manager.");
        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("evidence-first: refuses a NON_COMPLIANT verdict with zero evidence of any kind")
    void refusesNonCompliantWithoutEvidence() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        Inspection inspection = inspectionAssignedTo(INSPECTOR_ID);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspection));
        when(checklistEntryRepository.countPendingByInspectionId(INSPECTION_ID)).thenReturn(0L);
        when(evidenceRepository.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID)).thenReturn(List.of());
        when(inspectionEvidenceRepository.findByInspectionIdOrderByCapturedAtAsc(INSPECTION_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(INSPECTION_ID,
                new InspectionSubmitRequest(InspectionStatus.NON_COMPLIANT, null), INSPECTOR_ID))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getErrorCode())
                        .isEqualTo(com.lmguard.exception.ErrorCode.EVIDENCE_REQUIRED));
    }

    @Test
    @DisplayName("evidence-first: an officer-captured photo alone satisfies the guard, without any AI evidence")
    void allowsNonCompliantWithOnlyOfficerEvidence() {
        authenticateAs(INSPECTOR_ID, Role.INSPECTOR);
        Inspection inspection = inspectionAssignedTo(INSPECTOR_ID);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspection));
        when(checklistEntryRepository.countPendingByInspectionId(INSPECTION_ID)).thenReturn(0L);
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(inv -> inv.getArgument(0));
        when(evidenceRepository.findByInspectionIdOrderByCreatedAtAsc(INSPECTION_ID)).thenReturn(List.of());
        when(inspectionEvidenceRepository.findByInspectionIdOrderByCapturedAtAsc(INSPECTION_ID))
                .thenReturn(List.of(com.lmguard.entity.InspectionEvidence.builder().build()));

        service.submit(INSPECTION_ID, new InspectionSubmitRequest(InspectionStatus.NON_COMPLIANT, null), INSPECTOR_ID);

        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.NON_COMPLIANT);
    }

    @Test
    @DisplayName("updateNotes refuses an inspector the inspection isn't assigned to")
    void updateNotesRefusesWrongInspector() {
        authenticateAs(OTHER_INSPECTOR_ID, Role.INSPECTOR);
        Inspection inspection = inspectionAssignedTo(INSPECTOR_ID);
        when(inspectionRepository.findDetailedById(INSPECTION_ID)).thenReturn(Optional.of(inspection));

        assertThatThrownBy(() -> service.updateNotes(INSPECTION_ID,
                new com.lmguard.dto.inspection.InspectionNotesRequest("Trying to edit someone else's case"), OTHER_INSPECTOR_ID))
                .isInstanceOf(ApiException.class);
    }
}
