package com.lmguard.repository;

import com.lmguard.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    @Query("""
            SELECT p FROM Product p
            WHERE (:search IS NULL
                   OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:category IS NULL OR p.category = :category)
            """)
    Page<Product> search(@Param("search") String search,
                         @Param("category") String category,
                         Pageable pageable);
}
