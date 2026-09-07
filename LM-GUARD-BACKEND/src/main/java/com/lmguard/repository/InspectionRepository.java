package com.lmguard.repository;

import com.lmguard.entity.Inspection;
import com.lmguard.entity.enums.InspectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    /** True once any inspection has been judged under this ruleset version - the point past
     * which its rules must be treated as immutable, since editing them in place would silently
     * rewrite what an already-recorded verdict was actually judged against. */
    boolean existsByRulesetVersion(String rulesetVersion);

    /** Loads the aggregate needed to render a full inspection result in one round trip. */
    @Query("""
            SELECT DISTINCT i FROM Inspection i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH i.inspector
            LEFT JOIN FETCH i.zone
            WHERE i.id = :id
            """)
    Optional<Inspection> findDetailedById(@Param("id") UUID id);

    @Query(value = """
            SELECT i FROM Inspection i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH i.inspector
            LEFT JOIN FETCH i.zone
            WHERE (:status IS NULL OR i.status = :status)
              AND (:productId IS NULL OR i.product.id = :productId)
              AND (:inspectorId IS NULL OR i.inspector.id = :inspectorId)
              AND (:zoneId IS NULL OR i.zone.id = :zoneId)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Inspection i
            WHERE (:status IS NULL OR i.status = :status)
              AND (:productId IS NULL OR i.product.id = :productId)
              AND (:inspectorId IS NULL OR i.inspector.id = :inspectorId)
              AND (:zoneId IS NULL OR i.zone.id = :zoneId)
            """)
    Page<Inspection> searchDetailed(@Param("status") InspectionStatus status,
                                    @Param("productId") UUID productId,
                                    @Param("inspectorId") UUID inspectorId,
                                    @Param("zoneId") UUID zoneId,
                                    Pageable pageable);

    long countByStatus(InspectionStatus status);

    long countByCreatedAtAfter(Instant since);

    long countByProductIdAndStatus(UUID productId, InspectionStatus status);

    /** Prior completed inspections of this product, newest first, excluding the current one. */
    @Query("""
            SELECT i FROM Inspection i
            WHERE i.product.id = :productId
              AND i.id <> :excludeId
              AND i.status IN (com.lmguard.entity.enums.InspectionStatus.COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.NON_COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.INCONCLUSIVE)
            ORDER BY i.createdAt DESC
            """)
    List<Inspection> findPreviousForProduct(@Param("productId") UUID productId,
                                            @Param("excludeId") UUID excludeId);

    /** Every completed inspection of this product, newest first - the Product History screen's
     * full list (unlike {@link #findPreviousForProduct}, nothing is excluded). */
    @Query("""
            SELECT i FROM Inspection i
            LEFT JOIN FETCH i.inspector
            WHERE i.product.id = :productId
              AND i.status IN (com.lmguard.entity.enums.InspectionStatus.COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.NON_COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.INCONCLUSIVE)
            ORDER BY i.createdAt DESC
            """)
    List<Inspection> findCompletedForProduct(@Param("productId") UUID productId);

    @Query("""
            SELECT COUNT(i) FROM Inspection i
            WHERE i.product.id = :productId
              AND i.id <> :excludeId
              AND i.status = com.lmguard.entity.enums.InspectionStatus.NON_COMPLIANT
            """)
    long countPreviousNonCompliant(@Param("productId") UUID productId,
                                   @Param("excludeId") UUID excludeId);

    /** Cheap latest-inspection-date lookup for the Products registry list - a MAX aggregate
     * rather than {@link #findCompletedForProduct}, which loads full entities. */
    @Query("""
            SELECT MAX(i.createdAt) FROM Inspection i
            WHERE i.product.id = :productId
              AND i.status IN (com.lmguard.entity.enums.InspectionStatus.COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.NON_COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.INCONCLUSIVE)
            """)
    Instant findLatestCompletedAt(@Param("productId") UUID productId);

    /** The most recent package photo captured for this product, regardless of whether that
     * inspection was ever submitted - a photo exists as soon as it's uploaded, independent of
     * completion status, so gating this to completed inspections (like {@link #findLatestCompletedAt})
     * would hide the image on every still-in-progress case. Pass a single-row {@link Pageable}
     * (e.g. {@code PageRequest.of(0, 1)}) to get just the latest. */
    @Query("""
            SELECT i.imageUrl FROM Inspection i
            WHERE i.product.id = :productId AND i.imageUrl IS NOT NULL
            ORDER BY i.createdAt DESC
            """)
    List<String> findRecentImageUrls(@Param("productId") UUID productId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT i.status, COUNT(i) FROM Inspection i GROUP BY i.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT AVG(i.riskScore) FROM Inspection i WHERE i.riskScore IS NOT NULL")
    Double findAverageRiskScore();

    // ------------------------------------------------------------------
    // Analytics (programme-level reporting screen)
    // ------------------------------------------------------------------

    /** Case-open timestamps since a cutoff, bucketed into a daily series in Java rather than
     * with a DB-specific date_trunc so the query stays portable across Postgres and the H2
     * dialect the test suite runs against. */
    @Query("SELECT i.createdAt FROM Inspection i WHERE i.createdAt >= :since")
    List<Instant> findCreatedAtSince(@Param("since") Instant since);

    /** (opened, decided) timestamp pairs for every inspection that has reached a final verdict -
     * the raw material for "average days to resolve". */
    @Query("SELECT i.createdAt, i.completedAt FROM Inspection i WHERE i.completedAt IS NOT NULL")
    List<Object[]> findCreatedAndCompletedForDecided();

    /** Per-zone case volume and non-compliance count, for the Analytics "Regional insights" panel. */
    @Query("""
            SELECT i.zone.name, COUNT(i),
                   SUM(CASE WHEN i.status = com.lmguard.entity.enums.InspectionStatus.NON_COMPLIANT THEN 1 ELSE 0 END)
            FROM Inspection i
            WHERE i.zone IS NOT NULL
            GROUP BY i.zone.name
            """)
    List<Object[]> countAndViolationsByZone();

    // ------------------------------------------------------------------
    // Inspector workload (Inspector Management screen)
    // ------------------------------------------------------------------

    long countByInspector_Id(UUID inspectorId);

    @Query("""
            SELECT COUNT(i) FROM Inspection i
            WHERE i.inspector.id = :inspectorId
              AND i.status IN (com.lmguard.entity.enums.InspectionStatus.PENDING,
                               com.lmguard.entity.enums.InspectionStatus.PROCESSING)
            """)
    long countOpenByInspector(@Param("inspectorId") UUID inspectorId);

    @Query("""
            SELECT COUNT(i) FROM Inspection i
            WHERE i.inspector.id = :inspectorId
              AND i.status IN (com.lmguard.entity.enums.InspectionStatus.NON_COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.INCONCLUSIVE)
            """)
    long countAwaitingReviewByInspector(@Param("inspectorId") UUID inspectorId);

    @Query("""
            SELECT COUNT(i) FROM Inspection i
            WHERE i.inspector.id = :inspectorId
              AND i.status IN (com.lmguard.entity.enums.InspectionStatus.COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.NON_COMPLIANT,
                               com.lmguard.entity.enums.InspectionStatus.INCONCLUSIVE)
              AND i.completedAt >= :since
            """)
    long countCompletedByInspectorSince(@Param("inspectorId") UUID inspectorId, @Param("since") Instant since);

    List<Inspection> findTop5ByInspector_IdOrderByCreatedAtDesc(UUID inspectorId);
}
