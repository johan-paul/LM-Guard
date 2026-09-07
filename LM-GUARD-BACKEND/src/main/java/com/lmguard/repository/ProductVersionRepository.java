package com.lmguard.repository;

import com.lmguard.entity.ProductVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {

    List<ProductVersion> findByProductIdOrderByVersionNumberDesc(UUID productId);

    Optional<ProductVersion> findFirstByProductIdOrderByVersionNumberDesc(UUID productId);

    Optional<ProductVersion> findByProductIdAndVersionNumber(UUID productId, int versionNumber);

    long countByProductId(UUID productId);

    @Query("SELECT COALESCE(MAX(pv.versionNumber), 0) FROM ProductVersion pv WHERE pv.product.id = :productId")
    int findMaxVersionNumber(@Param("productId") UUID productId);

    // ------------------------------------------------------------------
    // Product History screen (cross-product declaration change ledger)
    // ------------------------------------------------------------------

    Page<ProductVersion> findAllByOrderByCapturedAtDesc(Pageable pageable);

    @Query(value = """
            SELECT pv FROM ProductVersion pv
            JOIN FETCH pv.product p
            WHERE (LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY pv.capturedAt DESC
            """,
            countQuery = """
            SELECT COUNT(pv) FROM ProductVersion pv
            JOIN pv.product p
            WHERE (LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<ProductVersion> searchAll(@Param("search") String search, Pageable pageable);
}
