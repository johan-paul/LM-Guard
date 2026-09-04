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

    /** Loads the aggregate needed to render a full inspection result in one round trip. */
    @Query("""
            SELECT DISTINCT i FROM Inspection i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH i.inspector
            WHERE i.id = :id
            """)
    Optional<Inspection> findDetailedById(@Param("id") UUID id);

    @Query(value = """
            SELECT i FROM Inspection i
            LEFT JOIN FETCH i.product
            LEFT JOIN FETCH i.inspector
            WHERE (:status IS NULL OR i.status = :status)
              AND (:productId IS NULL OR i.product.id = :productId)
              AND (:inspectorId IS NULL OR i.inspector.id = :inspectorId)
            """,
            countQuery = """
            SELECT COUNT(i) FROM Inspection i
            WHERE (:status IS NULL OR i.status = :status)
              AND (:productId IS NULL OR i.product.id = :productId)
              AND (:inspectorId IS NULL OR i.inspector.id = :inspectorId)
            """)
    Page<Inspection> searchDetailed(@Param("status") InspectionStatus status,
                                    @Param("productId") UUID productId,
                                    @Param("inspectorId") UUID inspectorId,
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

    @Query("""
            SELECT COUNT(i) FROM Inspection i
            WHERE i.product.id = :productId
              AND i.id <> :excludeId
              AND i.status = com.lmguard.entity.enums.InspectionStatus.NON_COMPLIANT
            """)
    long countPreviousNonCompliant(@Param("productId") UUID productId,
                                   @Param("excludeId") UUID excludeId);

    @Query("SELECT i.status, COUNT(i) FROM Inspection i GROUP BY i.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT AVG(i.riskScore) FROM Inspection i WHERE i.riskScore IS NOT NULL")
    Double findAverageRiskScore();
}
