package com.lmguard.service;

import com.lmguard.dto.inspector.InspectorCreateRequest;
import com.lmguard.dto.inspector.InspectorResponse;
import com.lmguard.entity.InspectorProfile;
import com.lmguard.entity.User;
import com.lmguard.entity.Zone;
import com.lmguard.entity.enums.Role;
import com.lmguard.exception.ConflictException;
import com.lmguard.mapper.InspectorMapper;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.InspectorProfileRepository;
import com.lmguard.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The inspector directory service: officer-code sequencing, account creation, and the
 * ACTIVE/INACTIVE status toggle that must actually disable field-app access (not just cosmetics).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inspector directory service")
class InspectorServiceTest {

    @Mock private InspectorProfileRepository inspectorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private InspectionRepository inspectionRepository;
    @Mock private ZoneService zoneService;
    @Mock private PasswordEncoder passwordEncoder;

    private final InspectorMapper inspectorMapper = new InspectorMapper();

    private InspectorService service() {
        return new InspectorService(inspectorProfileRepository, userRepository, inspectionRepository,
                zoneService, passwordEncoder, inspectorMapper);
    }

    private Zone zone(String name) {
        return Zone.builder().name(name).code("Z").office("Office").district("District").build();
    }

    @Test
    @DisplayName("continues the officer-code sequence from the highest existing suffix")
    void officerCodeContinuesSequence() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(inspectorProfileRepository.findAllOfficerCodes())
                .thenReturn(List.of("LM-INS-014", "LM-INS-101", "LM-INS-047"));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inspectorProfileRepository.save(any(InspectorProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(zoneService.requireByName("Coimbatore North")).thenReturn(zone("Coimbatore North"));
        when(inspectionRepository.countOpenByInspector(any())).thenReturn(0L);
        when(inspectionRepository.countCompletedByInspectorSince(any(), any())).thenReturn(0L);
        when(inspectionRepository.countByInspector_Id(any())).thenReturn(0L);
        when(inspectionRepository.countAwaitingReviewByInspector(any())).thenReturn(0L);

        InspectorResponse created = service().create(new InspectorCreateRequest(
                "New Officer", null, null, "new.officer@example.test", "+91 90000 00000",
                "Coimbatore North", "ACTIVE"));

        assertThat(created.id()).isEqualTo("LM-INS-102");
        assertThat(created.temporaryPassword()).isNotBlank();

        ArgumentCaptor<InspectorProfile> captor = ArgumentCaptor.forClass(InspectorProfile.class);
        org.mockito.Mockito.verify(inspectorProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getOfficerCode()).isEqualTo("LM-INS-102");
    }

    @Test
    @DisplayName("starts the sequence at LM-INS-101 when the directory is empty")
    void officerCodeStartsAtOneOhOneWhenEmpty() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(inspectorProfileRepository.findAllOfficerCodes()).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inspectorProfileRepository.save(any(InspectorProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inspectionRepository.countOpenByInspector(any())).thenReturn(0L);
        when(inspectionRepository.countCompletedByInspectorSince(any(), any())).thenReturn(0L);
        when(inspectionRepository.countByInspector_Id(any())).thenReturn(0L);
        when(inspectionRepository.countAwaitingReviewByInspector(any())).thenReturn(0L);

        InspectorResponse created = service().create(new InspectorCreateRequest(
                "First Officer", null, null, "first.officer@example.test", "+91 90000 00001", null, null));

        assertThat(created.id()).isEqualTo("LM-INS-101");
    }

    @Test
    @DisplayName("refuses to create an inspector whose email is already registered")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("dup@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service().create(new InspectorCreateRequest(
                "Someone", null, null, "dup@example.test", "+91 90000 00002", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("deactivating an inspector disables the underlying account, not just a display flag")
    void deactivateDisablesAccount() {
        User user = User.builder().name("S. Kumar").email("s.kumar@example.test")
                .passwordHash("hash").role(Role.INSPECTOR).enabled(true).build();
        InspectorProfile profile = InspectorProfile.builder()
                .user(user).officerCode("LM-INS-014").rank("Inspecting Officer")
                .joinedOn(LocalDate.now()).build();

        when(inspectorProfileRepository.findDetailedByOfficerCode("LM-INS-014"))
                .thenReturn(Optional.of(profile));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inspectionRepository.countOpenByInspector(any())).thenReturn(0L);
        when(inspectionRepository.countCompletedByInspectorSince(any(), any())).thenReturn(0L);
        when(inspectionRepository.countByInspector_Id(any())).thenReturn(0L);
        when(inspectionRepository.countAwaitingReviewByInspector(any())).thenReturn(0L);

        InspectorResponse response = service().setStatus("LM-INS-014", "INACTIVE");

        assertThat(user.isEnabled()).isFalse();
        assertThat(response.status()).isEqualTo("INACTIVE");
    }
}
