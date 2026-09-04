package com.lmguard.repository;

import com.lmguard.entity.ProductVersion;
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

    long countByProductId(UUID productId);

    @Query("SELECT COALESCE(MAX(pv.versionNumber), 0) FROM ProductVersion pv WHERE pv.product.id = :productId")
    int findMaxVersionNumber(@Param("productId") UUID productId);
}
