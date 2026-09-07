package com.lmguard.repository;

import com.lmguard.entity.Violation;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.ViolationCaseStatus;
import com.lmguard.entity.enums.ViolationStatus;
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
public interface ViolationRepository extends JpaRepository<Violation, UUID> {

    List<Violation> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

    long countByStatus(ViolationStatus status);

    long countByCaseStatus(ViolationCaseStatus caseStatus);

    /** Rule codes previously raised against this product, used for the repeat-issue risk factor. */
    @Query("""
            SELECT DISTINCT v.ruleCode FROM Violation v
            WHERE v.inspection.product.id = :productId
              AND v.inspection.id <> :excludeInspectionId
              AND v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
            """)
    List<String> findPreviousRuleCodesForProduct(@Param("productId") UUID productId,
                                                 @Param("excludeInspectionId") UUID excludeInspectionId);

    @Query("""
            SELECT v.ruleCode, COUNT(v) FROM Violation v
            WHERE v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
            GROUP BY v.ruleCode
            ORDER BY COUNT(v) DESC
            """)
    List<Object[]> countGroupedByRuleCode();

    @Query("""
            SELECT v.fieldName, COUNT(v) FROM Violation v
            WHERE v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
            GROUP BY v.fieldName
            ORDER BY COUNT(v) DESC
            """)
    List<Object[]> countGroupedByFieldName();

    // ------------------------------------------------------------------
    // Product History (Product History screen)
    // ------------------------------------------------------------------

    long countByInspection_Product_IdAndStatus(UUID productId, ViolationStatus status);

    /** Every violation raised against this product, across every inspection - the Product
     * Detail screen's Violations tab. */
    List<Violation> findByInspection_Product_IdOrderByCreatedAtDesc(UUID productId);

    /** Violations still open for review (OPEN/UNDER_REVIEW/ESCALATED, i.e. not yet CONFIRMED or
     * DISMISSED) - a real case-management count now that {@link ViolationCaseStatus} exists,
     * distinct from the rule engine's own NON_COMPLIANT/INCONCLUSIVE verdict. */
    long countByInspection_Product_IdAndCaseStatusIn(UUID productId, java.util.Collection<ViolationCaseStatus> caseStatuses);

    /** How many distinct inspections raised each rule code against this product - used to tell
     * a one-off violation apart from a genuine repeat (raised across more than one inspection). */
    @Query("""
            SELECT v.ruleCode, COUNT(DISTINCT v.inspection.id) FROM Violation v
            WHERE v.inspection.product.id = :productId
              AND v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
            GROUP BY v.ruleCode
            """)
    List<Object[]> countInspectionsPerRuleCodeForProduct(@Param("productId") UUID productId);

    // ------------------------------------------------------------------
    // Analytics (programme-level reporting screen)
    // ------------------------------------------------------------------

    /** Detection timestamps since a cutoff, bucketed into a daily series in Java (see
     * {@link InspectionRepository#findCreatedAtSince} for why this isn't a DB date_trunc). */
    @Query("""
            SELECT v.createdAt FROM Violation v
            WHERE v.createdAt >= :since AND v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
            """)
    List<Instant> findCreatedAtSinceNonCompliant(@Param("since") Instant since);

    /** Every recorded confidence score for a substantiated violation, for confidence-band bucketing. */
    @Query("""
            SELECT v.decisionConfidence FROM Violation v
            WHERE v.decisionConfidence IS NOT NULL
              AND v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
            """)
    List<java.math.BigDecimal> findConfidenceValues();

    /** (manufacturer, detected-at) pairs for every substantiated violation with a known brand -
     * the raw material for the repeat-offender trend: when a manufacturer's Nth violation lands. */
    @Query("""
            SELECT v.inspection.product.brand, v.createdAt FROM Violation v
            WHERE v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
              AND v.inspection.product IS NOT NULL
              AND v.inspection.product.brand IS NOT NULL
            """)
    List<Object[]> findBrandAndCreatedAtNonCompliant();

    /** Manufacturers with violations, most-cited first - "repeat offenders" for the Risk
     * Intelligence screen. Includes single-violation manufacturers; the service layer applies
     * whatever repeat threshold the UI needs so the query itself stays a plain aggregate. */
    @Query("""
            SELECT v.inspection.product.brand, COUNT(v), COUNT(DISTINCT v.inspection.product.id)
            FROM Violation v
            WHERE v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
              AND v.inspection.product IS NOT NULL
              AND v.inspection.product.brand IS NOT NULL
            GROUP BY v.inspection.product.brand
            ORDER BY COUNT(v) DESC
            """)
    List<Object[]> countAndProductsByBrand();

    /** Per-(manufacturer, zone) violation counts, used to find each manufacturer's predominant
     * zone - a manufacturer isn't zone-scoped itself, only the inspections that cite it are. */
    @Query("""
            SELECT v.inspection.product.brand, v.inspection.zone.name, COUNT(v)
            FROM Violation v
            WHERE v.status = com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT
              AND v.inspection.product IS NOT NULL
              AND v.inspection.product.brand IS NOT NULL
              AND v.inspection.zone IS NOT NULL
            GROUP BY v.inspection.product.brand, v.inspection.zone.name
            """)
    List<Object[]> countByBrandAndZone();

    // ------------------------------------------------------------------
    // Violations screen (case queue)
    // ------------------------------------------------------------------

    /** The case queue, newest first. Free-text search is applied client-side against this page,
     * the same convention {@code InspectionRepository#searchDetailed} already uses - see its
     * comment for why. */
    @Query(value = """
            SELECT DISTINCT v FROM Violation v
            LEFT JOIN FETCH v.inspection i
            LEFT JOIN FETCH i.product
            WHERE (:caseStatus IS NULL OR v.caseStatus = :caseStatus)
              AND (:riskLevel IS NULL OR i.riskLevel = :riskLevel)
              AND (:since IS NULL OR v.createdAt >= :since)
            ORDER BY v.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(v) FROM Violation v
            JOIN v.inspection i
            WHERE (:caseStatus IS NULL OR v.caseStatus = :caseStatus)
              AND (:riskLevel IS NULL OR i.riskLevel = :riskLevel)
              AND (:since IS NULL OR v.createdAt >= :since)
            """)
    Page<Violation> searchCases(@Param("caseStatus") ViolationCaseStatus caseStatus,
                                @Param("riskLevel") RiskLevel riskLevel,
                                @Param("since") Instant since,
                                Pageable pageable);

    @Query("""
            SELECT v FROM Violation v
            LEFT JOIN FETCH v.inspection i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH i.zone
            LEFT JOIN FETCH i.inspector
            LEFT JOIN FETCH v.rule
            LEFT JOIN FETCH v.evidence
            WHERE v.id = :id
            """)
    Optional<Violation> findDetailedById(@Param("id") UUID id);

    /** Other cases on record for the same product, newest first. */
    @Query("""
            SELECT v FROM Violation v
            WHERE v.inspection.product.id = :productId AND v.id <> :excludeId
            ORDER BY v.createdAt DESC
            """)
    List<Violation> findOtherForProduct(@Param("productId") UUID productId, @Param("excludeId") UUID excludeId);
}
