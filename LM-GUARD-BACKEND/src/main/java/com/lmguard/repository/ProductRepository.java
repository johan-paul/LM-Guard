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

    // :search is CAST to string explicitly: left untyped, Postgres can't infer a type for it
    // when it binds null (this query's only other use of :search is an `IS NULL` check, which
    // gives the driver no type context either), and guesses `bytea`, failing every call with
    // "function lower(bytea) does not exist" - reproduced by the mobile app's `?search=` (an
    // empty string, normalised to null before reaching this query).
    @Query("""
            SELECT p FROM Product p
            WHERE (:search IS NULL
                   OR LOWER(p.productName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
              AND (:category IS NULL OR p.category = :category)
            """)
    Page<Product> search(@Param("search") String search,
                         @Param("category") String category,
                         Pageable pageable);
}
