package com.lmguard.repository;

import com.lmguard.entity.Violation;
import com.lmguard.entity.enums.ViolationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ViolationRepository extends JpaRepository<Violation, UUID> {

    List<Violation> findByInspectionIdOrderByCreatedAtAsc(UUID inspectionId);

    long countByStatus(ViolationStatus status);

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
}
