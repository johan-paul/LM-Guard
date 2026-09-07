package com.lmguard.repository;

import com.lmguard.entity.AssignmentHistory;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.InspectorProfile;
import com.lmguard.entity.Product;
import com.lmguard.entity.User;
import com.lmguard.entity.Zone;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Zone/Inspector/assignment JPQL against a real (H2, PostgreSQL-mode) database,
 * rather than mocked repositories - {@link com.lmguard.service.InspectorServiceTest} pins down
 * the service logic, this pins down that the queries themselves are valid and return what the
 * service expects. Rolls back automatically; nothing here is left behind for other tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Zone/Inspector/assignment repositories")
class ZoneAndInspectorRepositoryIntegrationTest {

    @Autowired private ZoneRepository zoneRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private InspectorProfileRepository inspectorProfileRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InspectionRepository inspectionRepository;
    @Autowired private AssignmentHistoryRepository assignmentHistoryRepository;

    @Test
    @DisplayName("zone lookups: by name, existence, and the full ordered list")
    void zoneLookups() {
        Zone zone = zoneRepository.save(Zone.builder()
                .name("Test Zone Alpha").code("TZA").office("Alpha Office").district("Alphaville")
                .build());

        assertThat(zoneRepository.existsByNameIgnoreCase("test zone alpha")).isTrue();
        assertThat(zoneRepository.findByNameIgnoreCase("TEST ZONE ALPHA")).contains(zone);
        assertThat(zoneRepository.findAllOrderByName()).contains(zone);
    }

    @Test
    @DisplayName("inspector profile: detailed lookups by code and by user id")
    void inspectorProfileLookups() {
        Zone zone = zoneRepository.save(Zone.builder().name("Test Zone Beta").build());
        User inspector = userRepository.save(User.builder()
                .name("Test Officer").email("test.officer@example.test")
                .passwordHash("hash").role(Role.INSPECTOR).enabled(true).build());
        InspectorProfile profile = inspectorProfileRepository.save(InspectorProfile.builder()
                .user(inspector).officerCode("LM-INS-900").zone(zone)
                .rank("Inspecting Officer").joinedOn(LocalDate.now()).build());

        assertThat(inspectorProfileRepository.existsByOfficerCode("LM-INS-900")).isTrue();
        assertThat(inspectorProfileRepository.findAllOfficerCodes()).contains("LM-INS-900");

        InspectorProfile byCode = inspectorProfileRepository.findDetailedByOfficerCode("LM-INS-900").orElseThrow();
        assertThat(byCode.getUser().getEmail()).isEqualTo("test.officer@example.test");
        assertThat(byCode.getZone().getName()).isEqualTo("Test Zone Beta");

        InspectorProfile byUserId = inspectorProfileRepository.findDetailedByUserId(inspector.getId()).orElseThrow();
        assertThat(byUserId.getOfficerCode()).isEqualTo("LM-INS-900");

        assertThat(inspectorProfileRepository.findAllDetailed())
                .extracting(InspectorProfile::getOfficerCode)
                .contains("LM-INS-900");
    }

    @Test
    @DisplayName("inspection workload counts and zone filtering reflect real assigned rows")
    void inspectionWorkloadAndZoneFilter() {
        Zone zone = zoneRepository.save(Zone.builder().name("Test Zone Gamma").build());
        User admin = userRepository.save(User.builder()
                .name("Test Admin").email("test.admin@example.test")
                .passwordHash("hash").role(Role.ADMIN).enabled(true).build());
        User inspector = userRepository.save(User.builder()
                .name("Test Inspector Two").email("test.inspector.two@example.test")
                .passwordHash("hash").role(Role.INSPECTOR).enabled(true).build());
        Product product = productRepository.save(Product.builder().productName("Test Product").build());

        Inspection inspection = inspectionRepository.save(Inspection.builder()
                .product(product).inspector(inspector).zone(zone)
                .status(InspectionStatus.PENDING)
                .assignedBy(admin).assignedAt(Instant.now())
                .build());

        assertThat(inspectionRepository.countByInspector_Id(inspector.getId())).isEqualTo(1L);
        assertThat(inspectionRepository.countOpenByInspector(inspector.getId())).isEqualTo(1L);
        assertThat(inspectionRepository.countAwaitingReviewByInspector(inspector.getId())).isZero();
        assertThat(inspectionRepository.countCompletedByInspectorSince(inspector.getId(), Instant.EPOCH)).isZero();
        assertThat(inspectionRepository.findTop5ByInspector_IdOrderByCreatedAtDesc(inspector.getId()))
                .contains(inspection);

        var page = inspectionRepository.searchDetailed(
                InspectionStatus.PENDING, null, inspector.getId(), zone.getId(),
                PageRequest.of(0, 20));
        assertThat(page.getContent()).contains(inspection);

        // A different zone must not match.
        Zone otherZone = zoneRepository.save(Zone.builder().name("Test Zone Delta").build());
        var noMatch = inspectionRepository.searchDetailed(
                InspectionStatus.PENDING, null, inspector.getId(), otherZone.getId(),
                PageRequest.of(0, 20));
        assertThat(noMatch.getContent()).isEmpty();

        AssignmentHistory history = assignmentHistoryRepository.save(AssignmentHistory.builder()
                .inspection(inspection).fromInspector(null).toInspector(inspector)
                .fromZone(null).toZone(zone).assignedBy(admin).reason("Initial assignment")
                .build());

        List<AssignmentHistory> trail = assignmentHistoryRepository
                .findByInspectionIdOrderByCreatedAtDesc(inspection.getId());
        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getToInspector().getEmail()).isEqualTo("test.inspector.two@example.test");
        assertThat(trail.get(0).getAssignedBy().getEmail()).isEqualTo("test.admin@example.test");
        assertThat(history.getReason()).isEqualTo("Initial assignment");
    }
}
