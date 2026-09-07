package com.lmguard.service;

import com.lmguard.dto.product.ProductRiskResponse;
import com.lmguard.dto.product.ProductSummaryResponse;
import com.lmguard.dto.product.ProductVersionEntry;
import com.lmguard.entity.Product;
import com.lmguard.entity.ProductVersion;
import com.lmguard.entity.RiskScore;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
}
