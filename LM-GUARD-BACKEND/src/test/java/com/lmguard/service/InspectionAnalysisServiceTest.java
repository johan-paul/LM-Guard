package com.lmguard.service;

import com.lmguard.ai.AIAnalysisResult;
import com.lmguard.ai.MockAIAnalysisService;
import com.lmguard.config.properties.RiskProperties;
import com.lmguard.config.properties.RulesProperties;
import com.lmguard.dto.inspection.InspectionResponse;
import com.lmguard.dto.inspection.ViolationResponse;
import com.lmguard.entity.Evidence;
import com.lmguard.entity.ExtractedField;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.Product;
import com.lmguard.entity.RiskScore;
import com.lmguard.entity.User;
import com.lmguard.entity.Violation;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.ProductField;
import com.lmguard.entity.enums.Role;
import com.lmguard.entity.enums.RuleType;
import com.lmguard.entity.enums.Severity;
import com.lmguard.entity.enums.ViolationStatus;
import com.lmguard.evidence.EvidenceRequest;
import com.lmguard.evidence.EvidenceService;
import com.lmguard.mapper.EvidenceMapper;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.mapper.ProductMapper;
import com.lmguard.mapper.ViolationMapper;
import com.lmguard.repository.ExtractedFieldRepository;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.OnlineListingRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.ViolationRepository;
import com.lmguard.risk.WeightedRiskEngineService;
import com.lmguard.rules.DeterministicRuleEngineService;
import com.lmguard.rules.RuleCatalog;
import com.lmguard.rules.RuleDefinition;
import com.lmguard.rules.RuleSet;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Exercises the whole pipeline with only persistence mocked.
 *
 * <p>The mock AI, the real rule engine, the real risk engine and the real mappers all take
 * part, so this test covers what actually matters: that facts flow into a verdict, that every
 * finding gets evidence, and that a low-confidence reading never becomes a violation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inspection analysis pipeline (mock AI)")
class InspectionAnalysisServiceTest {

    private static final String VERSION = "DEMO-2026.1";

    @Mock private InspectionRepository inspectionRepository;
    @Mock private ExtractedFieldRepository extractedFieldRepository;
    @Mock private ViolationRepository violationRepository;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private OnlineListingRepository onlineListingRepository;
    @Mock private ProductService productService;
    @Mock private EvidenceService evidenceService;
    @Mock private RuleCatalog ruleCatalog;

    private InspectionAnalysisService service;

    @BeforeEach
    void setUp() {
        RulesProperties rulesProperties =
                new RulesProperties(VERSION, "classpath:rules/sample-rules.json", false, 0.70);

        DeterministicRuleEngineService ruleEngine =
                new DeterministicRuleEngineService(ruleCatalog, rulesProperties);

        WeightedRiskEngineService riskEngine = new WeightedRiskEngineService(new RiskProperties(
                new RiskProperties.Weights(30, 20, 25, 10, 15),
                new RiskProperties.Thresholds(30, 60),
                100,
                "PACKAGED_FOOD,FOOD,DAIRY"));

        ProductMapper productMapper = new ProductMapper();
        InspectionMapper inspectionMapper =
                new InspectionMapper(productMapper, new ViolationMapper(new EvidenceMapper()));

        service = new InspectionAnalysisService(
                inspectionRepository,
                extractedFieldRepository,
                violationRepository,
                riskScoreRepository,
                onlineListingRepository,
                productService,
                new MockAIAnalysisService(),
                ruleEngine,
                evidenceService,
                riskEngine,
                inspectionMapper,
                rulesProperties);

        when(ruleCatalog.load(anyString())).thenReturn(demoRuleSet());

        // Persistence: echo entities straight back so the pipeline can be observed end to end.
        when(extractedFieldRepository.saveAll(any()))
                .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
        when(extractedFieldRepository.findByInspectionIdOrderByFieldNameAsc(any())).thenReturn(List.of());
        when(violationRepository.findByInspectionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(violationRepository.findPreviousRuleCodesForProduct(any(), any())).thenReturn(List.of());
        when(violationRepository.save(any(Violation.class))).thenAnswer(invocation -> {
            Violation violation = invocation.getArgument(0);
            violation.setId(UUID.randomUUID());
            return violation;
        });
        when(riskScoreRepository.findByInspectionId(any())).thenReturn(Optional.empty());
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(onlineListingRepository.findFirstByProductIdOrderByCapturedAtDesc(any())).thenReturn(Optional.empty());
        when(inspectionRepository.save(any(Inspection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inspectionRepository.countPreviousNonCompliant(any(), any())).thenReturn(0L);
        when(productService.changeCount(any())).thenReturn(0L);
        when(productService.recordVersionIfChanged(any(), any(), any())).thenReturn(Optional.empty());
        when(evidenceService.record(any(EvidenceRequest.class))).thenAnswer(invocation -> {
            EvidenceRequest request = invocation.getArgument(0);
            Evidence evidence = Evidence.builder()
                    .violation(request.violation())
                    .inspection(request.inspection())
                    .imageUrl(request.imageUrl())
                    .imagePath(request.imagePath())
                    .x(request.box().x())
                    .y(request.box().y())
                    .width(request.box().width())
                    .height(request.box().height())
                    .description(request.description())
                    .build();
            evidence.setId(UUID.randomUUID());
            return evidence;
        });
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("a package missing its consumer care declaration comes back NON_COMPLIANT with evidence")
    void nonCompliantPackage() {
        Inspection inspection = inspectionWithVariant(0);
        when(inspectionRepository.findDetailedById(inspection.getId())).thenReturn(Optional.of(inspection));

        InspectionResponse response = service.run(inspection.getId());

        assertThat(response.status())
                .as("the rule engine's verdict is advisory only - status stays IN_PROGRESS until the inspector submits")
                .isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(response.aiSuggestedStatus()).isEqualTo(InspectionStatus.NON_COMPLIANT);
        assertThat(response.rulesetVersion()).isEqualTo(VERSION);
        assertThat(response.aiProvider()).isEqualTo(AIAnalysisResult.PROVIDER_MOCK);
        assertThat(response.overallConfidence()).isNotNull();

        ViolationResponse violation = response.violations().stream()
                .filter(item -> item.fieldName().equals(ProductField.CONSUMER_CARE))
                .findFirst()
                .orElseThrow();
        assertThat(violation.status()).isEqualTo(ViolationStatus.NON_COMPLIANT);
        assertThat(violation.evidence()).as("every finding carries evidence").isNotNull();
        assertThat(violation.decisionConfidence()).isNotNull();

        assertThat(response.risk()).isNotNull();
        assertThat(response.riskScore()).isNotNull().isBetween(0, 100);
        assertThat(response.riskLevel()).isNotNull();
        assertThat(response.analyzedAt()).isNotNull();
        assertThat(response.completedAt()).as("only /submit sets completedAt").isNull();
    }

    @Test
    @DisplayName("a fully declared package comes back COMPLIANT with no violations")
    void compliantPackage() {
        Inspection inspection = inspectionWithVariant(1);
        when(inspectionRepository.findDetailedById(inspection.getId())).thenReturn(Optional.of(inspection));

        InspectionResponse response = service.run(inspection.getId());

        assertThat(response.status()).isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(response.aiSuggestedStatus()).isEqualTo(InspectionStatus.COMPLIANT);
        assertThat(response.violations()).isEmpty();
    }

    @Test
    @DisplayName("a poorly read package comes back INCONCLUSIVE, never NON_COMPLIANT")
    void inconclusivePackage() {
        Inspection inspection = inspectionWithVariant(2);
        when(inspectionRepository.findDetailedById(inspection.getId())).thenReturn(Optional.of(inspection));

        InspectionResponse response = service.run(inspection.getId());

        assertThat(response.status()).isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(response.aiSuggestedStatus()).isEqualTo(InspectionStatus.INCONCLUSIVE);
        assertThat(response.violations()).isNotEmpty();
        assertThat(response.violations())
                .allSatisfy(violation ->
                        assertThat(violation.status()).isEqualTo(ViolationStatus.INCONCLUSIVE));
    }

    @Test
    @DisplayName("refuses to analyse an inspection with no image")
    void requiresAnImage() {
        Inspection inspection = inspectionWithVariant(0);
        inspection.setImageUrl(null);
        when(inspectionRepository.findDetailedById(inspection.getId())).thenReturn(Optional.of(inspection));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                com.lmguard.exception.BadRequestException.class,
                () -> service.run(inspection.getId())).getErrorCode())
                .isEqualTo(com.lmguard.exception.ErrorCode.IMAGE_REQUIRED);
    }

    @Test
    @DisplayName("404s for an unknown inspection")
    void unknownInspection() {
        UUID missing = UUID.randomUUID();
        when(inspectionRepository.findDetailedById(missing)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.lmguard.exception.ResourceNotFoundException.class, () -> service.run(missing));
    }

    @Test
    @DisplayName("persists the observations the verdict rests on")
    void persistsExtractedFields() {
        Inspection inspection = inspectionWithVariant(0);
        when(inspectionRepository.findDetailedById(inspection.getId())).thenReturn(Optional.of(inspection));

        InspectionResponse response = service.run(inspection.getId());

        assertThat(response.fields()).isNotEmpty();
        assertThat(response.fields())
                .anySatisfy(field -> assertThat(field.name()).isEqualTo(ProductField.MRP));
        org.mockito.Mockito.verify(extractedFieldRepository).deleteByInspectionId(inspection.getId());
        org.mockito.Mockito.verify(extractedFieldRepository).saveAll(any());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /**
     * The mock AI picks its scenario from the inspection id, so the test searches for an id
     * that yields the variant it wants. That keeps the mock deterministic in production while
     * still letting tests select a scenario.
     */
    private Inspection inspectionWithVariant(int variant) {
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (Math.floorMod(id.hashCode(), 3) != variant);

        Product product = Product.builder()
                .productName("Classic Salted Chips")
                .brand("ABC Foods")
                .category("PACKAGED_FOOD")
                .build();
        product.setId(UUID.randomUUID());

        User inspector = User.builder()
                .name("Test Inspector")
                .email("inspector@example.test")
                .passwordHash("x")
                .role(Role.INSPECTOR)
                .enabled(true)
                .build();
        inspector.setId(UUID.randomUUID());

        Inspection inspection = Inspection.builder()
                .product(product)
                .inspector(inspector)
                .status(InspectionStatus.PROCESSING)
                .imageUrl("http://localhost:8080/files/package-images/test.jpg")
                .imagePath("2026/09/04/test.jpg")
                .build();
        inspection.setId(id);
        return inspection;
    }

    /** Mirrors the shipped demo ruleset for the declarations the mock AI reports on. */
    private RuleSet demoRuleSet() {
        return new RuleSet(VERSION, "SAMPLE", "DEMO RULES - not official regulations", List.of(), List.of(
                required("DEMO-RULE-001", ProductField.CONSUMER_CARE),
                required("DEMO-MRP-001", ProductField.MRP),
                required("DEMO-QTY-001", ProductField.NET_QUANTITY),
                required("DEMO-MFR-001", ProductField.MANUFACTURER),
                required("DEMO-ORG-001", ProductField.ORIGIN),
                required("DEMO-DATE-001", ProductField.MANUFACTURE_DATE)));
    }

    private RuleDefinition required(String code, String field) {
        return new RuleDefinition(null, code, field + " required", "SAMPLE RULE", field,
                RuleType.REQUIRED_FIELD, true, 0.70, null, null, null, null, Severity.MAJOR,
                "Required declaration not detected", "Print the declaration on the package");
    }

    @Test
    @DisplayName("extracted field entities keep their bounding boxes")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void boundingBoxesSurvive() {
        Inspection inspection = inspectionWithVariant(0);
        when(inspectionRepository.findDetailedById(inspection.getId())).thenReturn(Optional.of(inspection));

        service.run(inspection.getId());

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(extractedFieldRepository).saveAll(captor.capture());
        List<ExtractedField> saved = captor.getValue();

        assertThat(saved)
                .anySatisfy(field -> {
                    assertThat(field.getFieldName()).isEqualTo(ProductField.MRP);
                    assertThat(field.hasBoundingBox()).isTrue();
                    assertThat(field.getConfidence()).isNotNull();
                });
    }
}
