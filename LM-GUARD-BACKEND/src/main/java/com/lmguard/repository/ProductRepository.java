package com.lmguard.repository;

import com.lmguard.entity.Product;
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
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    Page<Product> findByCategory(String category, Pageable pageable);

    /** Case/edge-whitespace-insensitive exact match on name+brand, used by
     * {@link com.lmguard.service.ProductService#findOrCreate} to avoid minting a duplicate
     * product every time the AI's OCR reading of an already-registered product's name/brand
     * differs only in case or trimming from a previous reading. Deliberately exact (no
     * substring/fuzzy matching, unlike {@link #search}) - a false merge of two genuinely
     * different products would corrupt both products' compliance history, which is worse than
     * occasionally missing a real duplicate. :brand may be null (a product with no manufacturer
     * reading yet only matches another equally brand-less entry with the same name).
     *
     * <p>Returns a list, not {@code Optional}, and the caller takes the oldest match: this ran
     * for the first time against a database that may already contain duplicates from before
     * this method existed (the exact problem it fixes going forward), and an {@code Optional}
     * throws at runtime if more than one row matches - which pre-existing duplicate data would
     * trigger on every single inspection of that product. */
    @Query("""
            SELECT p FROM Product p
            WHERE LOWER(TRIM(p.productName)) = LOWER(TRIM(:name))
              AND ((:brand IS NULL AND p.brand IS NULL) OR LOWER(TRIM(p.brand)) = LOWER(TRIM(:brand)))
            ORDER BY p.createdAt ASC
            """)
    List<Product> findByNameAndBrandExact(@Param("name") String name, @Param("brand") String brand);

    @Query("""
            SELECT p FROM Product p
            WHERE (LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:category IS NULL OR p.category = :category)
            """)
    Page<Product> search(@Param("search") String search,
                         @Param("category") String category,
                         Pageable pageable);
}
