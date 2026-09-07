package com.lmguard.service;

import com.lmguard.dto.violation.ViolationDecisionRequest;
import com.lmguard.dto.violation.ViolationDetailResponse;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.Product;
import com.lmguard.entity.User;
import com.lmguard.entity.Violation;
import com.lmguard.entity.enums.Role;
import com.lmguard.entity.enums.Severity;
import com.lmguard.entity.enums.ViolationCaseStatus;
import com.lmguard.entity.enums.ViolationStatus;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.EvidenceMapper;
import com.lmguard.mapper.ViolationCaseMapper;
import com.lmguard.repository.UserRepository;
import com.lmguard.repository.ViolationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The case queue's decision workflow: a violation moves OPEN -> (UNDER_REVIEW | CONFIRMED |
 * DISMISSED | ESCALATED) independently of the rule engine's own NON_COMPLIANT/INCONCLUSIVE
 * verdict, which this service never touches.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Violation case service")
class ViolationCaseServiceTest {

    @Mock private ViolationRepository violationRepository;
    @Mock private UserRepository userRepository;

    private final ViolationCaseMapper mapper = new ViolationCaseMapper(new EvidenceMapper());

    private ViolationCaseService service() {
        return new ViolationCaseService(violationRepository, userRepository, mapper);
    }

    private Violation violation(UUID id, ViolationCaseStatus caseStatus) {
        Product product = Product.builder().productName("Test Oil").brand("Acme").build();
        Inspection inspection = Inspection.builder().product(product).riskLevel(null).build();
        Violation v = Violation.builder()
                .inspection(inspection)
                .ruleCode("DEMO-RULE-001")
                .fieldName("CONSUMER_CARE")
                .finding("Not detected")
                .status(ViolationStatus.NON_COMPLIANT)
                .severity(Severity.MAJOR)
                .caseStatus(caseStatus)
                .build();
        v.setId(id);
        return v;
    }

    @Test
    @DisplayName("decide moves the violation to the requested case status and records who decided it")
    void decideRecordsTransition() {
        UUID violationId = UUID.randomUUID();
        UUID deciderId = UUID.randomUUID();
        Violation existing = violation(violationId, ViolationCaseStatus.OPEN);
        User decider = new User();
        decider.setId(deciderId);
        decider.setName("A. Officer");
        decider.setRole(Role.ADMIN);

        when(violationRepository.findDetailedById(violationId)).thenReturn(Optional.of(existing));
        when(userRepository.findById(deciderId)).thenReturn(Optional.of(decider));
        when(violationRepository.findOtherForProduct(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        ViolationDetailResponse result = service().decide(
                violationId, new ViolationDecisionRequest(ViolationCaseStatus.CONFIRMED, "Confirmed on review"), deciderId);

        assertThat(result.caseStatus()).isEqualTo(ViolationCaseStatus.CONFIRMED);
        assertThat(result.decidedBy()).isEqualTo("A. Officer");
        assertThat(result.decisionNote()).isEqualTo("Confirmed on review");
        assertThat(result.decidedAt()).isNotNull();

        ArgumentCaptor<Violation> saved = ArgumentCaptor.forClass(Violation.class);
        verify(violationRepository).save(saved.capture());
        assertThat(saved.getValue().getCaseStatus()).isEqualTo(ViolationCaseStatus.CONFIRMED);
    }

    @Test
    @DisplayName("decide never touches the rule engine's own status verdict")
    void decideLeavesEngineStatusUntouched() {
        UUID violationId = UUID.randomUUID();
        Violation existing = violation(violationId, ViolationCaseStatus.OPEN);
        when(violationRepository.findDetailedById(violationId)).thenReturn(Optional.of(existing));
        when(violationRepository.findOtherForProduct(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        ViolationDetailResponse result = service().decide(
                violationId, new ViolationDecisionRequest(ViolationCaseStatus.DISMISSED, null), null);

        assertThat(result.status()).isEqualTo(ViolationStatus.NON_COMPLIANT);
    }

    @Test
    @DisplayName("detail throws ResourceNotFoundException for an unknown id")
    void detailThrowsWhenMissing() {
        UUID missingId = UUID.randomUUID();
        when(violationRepository.findDetailedById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(missingId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
