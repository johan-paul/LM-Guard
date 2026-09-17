package com.lmguard.service;

import com.lmguard.dto.product.ProductRiskResponse;
import com.lmguard.dto.product.ProductSummaryResponse;
import com.lmguard.dto.product.ProductVersionEntry;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.Product;
import com.lmguard.entity.ProductVersion;
import com.lmguard.entity.RiskScore;
import com.lmguard.entity.enums.ProductField;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.VersionSource;
import com.lmguard.entity.enums.ViolationCaseStatus;
import com.lmguard.entity.enums.ViolationStatus;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.mapper.ProductMapper;
import com.lmguard.mapper.ViolationCaseMapper;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.OnlineListingRepository;
import com.lmguard.repository.ProductRepository;
import com.lmguard.repository.ProductVersionRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.ViolationRepository;
import com.lmguard.ai.AIAnalysisService;
import com.lmguard.storage.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Products registry's compliance profile: a real per-product risk/violation aggregate, not
 * a mock number - and the History ledger's previous-version linkage, which is what lets the UI
 * show a real "unchanged" vs "changed" delta rather than a guessed one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Product service")
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVersionRepository productVersionRepository;
    @Mock private InspectionRepository inspectionRepository;
    @Mock private ViolationRepository violationRepository;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private OnlineListingRepository onlineListingRepository;
    @Mock private InspectionMapper inspectionMapper;
    @Mock private ViolationCaseMapper violationCaseMapper;
    @Mock private AIAnalysisService aiAnalysisService;
    @Mock private FileStorageService fileStorageService;

    private final ProductMapper productMapper = new ProductMapper();

    private ProductService service() {
        return new ProductService(productRepository, productVersionRepository, inspectionRepository,
                violationRepository, riskScoreRepository, onlineListingRepository, productMapper,
                inspectionMapper, violationCaseMapper, aiAnalysisService, fileStorageService);
    }

    private Product product(UUID id) {
        Product p = Product.builder().productName("Test Oil").brand("Acme").category("EDIBLE_OILS").build();
        p.setId(id);
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        return p;
    }

    @Test
    @DisplayName("searchSummaries attaches each product's real risk score and violation counts, not a placeholder")
    void searchSummariesAttachesRealAggregates() {
        UUID productId = UUID.randomUUID();
        Product p = product(productId);
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of(p), pageable, 1));

        RiskScore score = RiskScore.builder().totalScore(72).riskLevel(RiskLevel.HIGH).build();
        when(riskScoreRepository.findByProductIdOrderByCreatedAtDesc(productId)).thenReturn(List.of(score));
        when(violationRepository.countByInspection_Product_IdAndStatus(productId, ViolationStatus.NON_COMPLIANT)).thenReturn(4L);
        when(violationRepository.countByInspection_Product_IdAndCaseStatusIn(any(), anyList())).thenReturn(2L);
        Instant lastInspection = Instant.now();
        when(inspectionRepository.findLatestCompletedAt(productId)).thenReturn(lastInspection);

        ProductSummaryResponse summary = service().searchSummaries(null, null, pageable).getContent().get(0);

        assertThat(summary.riskScore()).isEqualTo(72);
        assertThat(summary.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(summary.violationCount()).isEqualTo(4L);
        assertThat(summary.openViolationCount()).isEqualTo(2L);
        assertThat(summary.lastInspectionAt()).isEqualTo(lastInspection);
    }

    @Test
    @DisplayName("searchSummaries reports an unscored product honestly (null), not a fabricated zero score")
    void searchSummariesHandlesUnscoredProduct() {
        UUID productId = UUID.randomUUID();
        Product p = product(productId);
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of(p), pageable, 1));
        when(riskScoreRepository.findByProductIdOrderByCreatedAtDesc(productId)).thenReturn(List.of());
        when(violationRepository.countByInspection_Product_IdAndCaseStatusIn(any(), anyList())).thenReturn(0L);

        ProductSummaryResponse summary = service().searchSummaries(null, null, pageable).getContent().get(0);

        assertThat(summary.riskScore()).isNull();
        assertThat(summary.riskLevel()).isNull();
        assertThat(summary.lastInspectionAt()).isNull();
    }

    @Test
    @DisplayName("risk() lists only the factors that actually contributed points")
    void riskListsOnlyContributingFactors() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId)));
        RiskScore score = RiskScore.builder()
                .totalScore(55)
                .riskLevel(RiskLevel.MEDIUM)
                .previousViolations(30)
                .productChanges(0)
                .onlineMismatch(0)
                .categoryRisk(10)
                .repeatIssue(15)
                .explanation("Prior violations plus a repeat rule failure.")
                .build();
        when(riskScoreRepository.findByProductIdOrderByCreatedAtDesc(productId)).thenReturn(List.of(score));

        ProductRiskResponse risk = service().risk(productId);

        assertThat(risk.factors()).containsExactlyInAnyOrder(
                "Repeated declaration violations", "Product category risk", "Same rule failing again");
        assertThat(risk.factors()).doesNotContain("Recent package changes", "Physical–digital mismatch");
    }

    @Test
    @DisplayName("risk() returns null for a product that has never been scored")
    void riskReturnsNullWhenUnscored() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId)));
        when(riskScoreRepository.findByProductIdOrderByCreatedAtDesc(productId)).thenReturn(List.of());

        assertThat(service().risk(productId)).isNull();
    }

    @Test
    @DisplayName("historyLedger links each version to its own immediately preceding snapshot, not just any earlier one")
    void historyLedgerLinksPreviousVersion() {
        UUID productId = UUID.randomUUID();
        Product p = product(productId);
        ProductVersion v2 = ProductVersion.builder()
                .product(p).versionNumber(2).mrp("150").netQuantity("500 g").source(VersionSource.INSPECTION).build();
        v2.setId(UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 50);
        when(productVersionRepository.searchAll(any(), any())).thenReturn(new PageImpl<>(List.of(v2), pageable, 1));

        ProductVersion v1 = ProductVersion.builder()
                .product(p).versionNumber(1).mrp("120").netQuantity("450 g").source(VersionSource.INSPECTION).build();
        when(productVersionRepository.findByProductIdAndVersionNumber(productId, 1)).thenReturn(Optional.of(v1));

        ProductVersionEntry entry = service().historyLedger(null, pageable).getContent().get(0);

        assertThat(entry.mrp()).isEqualTo("150");
        assertThat(entry.previousMrp()).isEqualTo("120");
        assertThat(entry.previousNetQuantity()).isEqualTo("450 g");
    }

    @Test
    @DisplayName("historyLedger leaves the previous snapshot null for a product's first (baseline) version")
    void historyLedgerBaselineHasNoPrevious() {
        UUID productId = UUID.randomUUID();
        Product p = product(productId);
        ProductVersion v1 = ProductVersion.builder()
                .product(p).versionNumber(1).mrp("120").netQuantity("450 g").source(VersionSource.INSPECTION).build();
        Pageable pageable = PageRequest.of(0, 50);
        when(productVersionRepository.searchAll(any(), any())).thenReturn(new PageImpl<>(List.of(v1), pageable, 1));

        ProductVersionEntry entry = service().historyLedger(null, pageable).getContent().get(0);

        assertThat(entry.previousMrp()).isNull();
        assertThat(entry.previousNetQuantity()).isNull();
    }

    // ------------------------------------------------------------------
    // findOrCreate - product identity resolution for AI/inspector-guessed identity, never for
    // an admin's deliberate registration via create() (which stays a blind insert).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findOrCreate matches an existing product by barcode before considering name/brand at all")
    void findOrCreateMatchesByBarcode() {
        UUID existingId = UUID.randomUUID();
        Product existing = product(existingId);
        when(productRepository.findByBarcode("8901030826829")).thenReturn(Optional.of(existing));

        Product result = service().findOrCreate("Some Other Name", "Some Other Brand", "8901030826829");

        assertThat(result.getId()).isEqualTo(existingId);
        verify(productRepository, never()).findByNameAndBrandExact(any(), any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate matches an existing product by name+brand, trimming edge whitespace before querying")
    void findOrCreateMatchesByNameAndBrand() {
        UUID existingId = UUID.randomUUID();
        Product existing = product(existingId);
        when(productRepository.findByBarcode(any())).thenReturn(Optional.empty());
        when(productRepository.findByNameAndBrandExact("Test Oil", "Acme")).thenReturn(List.of(existing));

        Product result = service().findOrCreate("  Test Oil  ", "  Acme  ", null);

        assertThat(result.getId()).isEqualTo(existingId);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate creates a new product only when nothing matches by barcode or name+brand")
    void findOrCreateCreatesNewWhenNoMatch() {
        when(productRepository.findByBarcode(any())).thenReturn(Optional.empty());
        when(productRepository.findByNameAndBrandExact(any(), any())).thenReturn(List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        Product result = service().findOrCreate("Brand New Snack", "NewCo", "1112223334445");

        assertThat(result.getProductName()).isEqualTo("Brand New Snack");
        assertThat(result.getBrand()).isEqualTo("NewCo");
        assertThat(result.getBarcode()).isEqualTo("1112223334445");
    }

    // ------------------------------------------------------------------
    // recordVersionIfChanged - the confidence gate: a low-confidence (or absent) reading must
    // never silently overwrite what a product's declared history already reliably had.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("recordVersionIfChanged ignores a changed-but-low-confidence reading, keeping the previous value")
    void recordVersionIfChangedIgnoresLowConfidenceChange() {
        UUID productId = UUID.randomUUID();
        Product p = product(productId);
        ProductVersion previous = ProductVersion.builder()
                .product(p).versionNumber(1).mrp("99").netQuantity("500 g").source(VersionSource.INSPECTION).build();
        when(productVersionRepository.findFirstByProductIdOrderByVersionNumberDesc(productId))
                .thenReturn(Optional.of(previous));

        // A misread of 199 with confidence well below the gate - must not become "current".
        Optional<ProductVersion> result = service().recordVersionIfChanged(
                p, null, Map.of(ProductField.MRP, "199"), Map.of(ProductField.MRP, 0.2),
                0.70, VersionSource.INSPECTION);

        assertThat(result).isEmpty();
        verify(productVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordVersionIfChanged accepts a changed, confident reading and records which inspection produced it")
    void recordVersionIfChangedAcceptsConfidentChange() {
        UUID productId = UUID.randomUUID();
        Product p = product(productId);
        ProductVersion previous = ProductVersion.builder()
                .product(p).versionNumber(1).mrp("99").source(VersionSource.INSPECTION).build();
        when(productVersionRepository.findFirstByProductIdOrderByVersionNumberDesc(productId))
                .thenReturn(Optional.of(previous));
        when(productVersionRepository.findMaxVersionNumber(productId)).thenReturn(1);
        when(productVersionRepository.save(any(ProductVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Inspection inspection = Inspection.builder().build();
        inspection.setId(UUID.randomUUID());

        Optional<ProductVersion> result = service().recordVersionIfChanged(
                p, inspection, Map.of(ProductField.MRP, "149"), Map.of(ProductField.MRP, 0.95),
                0.70, VersionSource.INSPECTION);

        assertThat(result).isPresent();
        assertThat(result.get().getMrp()).isEqualTo("149");
        assertThat(result.get().getInspection()).isEqualTo(inspection);
    }

    @Test
    @DisplayName("recordVersionIfChanged never writes when nothing actually changed, regardless of confidence")
    void recordVersionIfChangedSkipsUnchangedValues() {
        UUID productId = UUID.randomUUID();
        Product p = product(productId);
        ProductVersion previous = ProductVersion.builder()
                .product(p).versionNumber(1).mrp("99").source(VersionSource.INSPECTION).build();
        when(productVersionRepository.findFirstByProductIdOrderByVersionNumberDesc(productId))
                .thenReturn(Optional.of(previous));

        Optional<ProductVersion> result = service().recordVersionIfChanged(
                p, null, Map.of(ProductField.MRP, "99"), Map.of(ProductField.MRP, 0.95),
                0.70, VersionSource.INSPECTION);

        assertThat(result).isEmpty();
        verify(productVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordVersionIfChanged preserves the previous value for a field the AI simply didn't read this time")
    void recordVersionIfChangedPreservesUndetectedField() {
        // Reproduces a real pre-existing bug: a field absent from this run's reading (e.g. glare
        // obscured the manufacturer this time) must not silently blank out a previously-recorded
        // value just because it's missing from `declaredValues` this run.
        UUID productId = UUID.randomUUID();
        Product p = product(productId);
        ProductVersion previous = ProductVersion.builder()
                .product(p).versionNumber(1).mrp("99").manufacturer("ABC Foods Pvt Ltd")
                .source(VersionSource.INSPECTION).build();
        when(productVersionRepository.findFirstByProductIdOrderByVersionNumberDesc(productId))
                .thenReturn(Optional.of(previous));
        when(productVersionRepository.findMaxVersionNumber(productId)).thenReturn(1);
        when(productVersionRepository.save(any(ProductVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // MRP changed (confidently); MANUFACTURER wasn't detected at all this run.
        Optional<ProductVersion> result = service().recordVersionIfChanged(
                p, null, Map.of(ProductField.MRP, "149"), Map.of(ProductField.MRP, 0.95),
                0.70, VersionSource.INSPECTION);

        assertThat(result).isPresent();
        assertThat(result.get().getMrp()).isEqualTo("149");
        assertThat(result.get().getManufacturer())
                .as("manufacturer must carry forward, not be blanked, since this run said nothing about it")
                .isEqualTo("ABC Foods Pvt Ltd");
    }
}
