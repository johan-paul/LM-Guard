package com.lmguard.service;

import com.lmguard.dto.product.ProductCreateRequest;
import com.lmguard.dto.product.ProductHistoryResponse;
import com.lmguard.dto.product.ProductResponse;
import com.lmguard.dto.product.ProductVersionResponse;
import com.lmguard.entity.Product;
import com.lmguard.entity.ProductVersion;
import com.lmguard.entity.enums.VersionSource;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.ProductMapper;
import com.lmguard.repository.ProductRepository;
import com.lmguard.repository.ProductVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Product registry and declaration history.
 *
 * <p>Declared values are never updated in place. Each capture appends a
 * {@link ProductVersion}, and a new version is only written when something actually changed -
 * which is what makes "this label was altered between inspections" a fact the system can
 * state rather than infer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVersionRepository productVersionRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = productRepository.save(Product.builder()
                .productName(request.productName().trim())
                .brand(trimToNull(request.brand()))
                .category(normaliseCategory(request.category()))
                .barcode(trimToNull(request.barcode()))
                .build());

        log.info("Registered product {} ({})", product.getProductName(), product.getId());
        return productMapper.toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String search, String category, Pageable pageable) {
        return productRepository.search(
                trimToNull(search), normaliseCategory(category), pageable);
    }

    @Transactional(readOnly = true)
    public Product requireById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.PRODUCT_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return productMapper.toResponse(requireById(id));
    }

    @Transactional(readOnly = true)
    public ProductHistoryResponse history(UUID productId) {
        Product product = requireById(productId);
        List<ProductVersion> versions =
                productVersionRepository.findByProductIdOrderByVersionNumberDesc(productId);

        List<ProductVersionResponse> responses = versions.stream()
                .map(productMapper::toResponse)
                .toList();

        return new ProductHistoryResponse(
                productMapper.toResponse(product),
                responses,
                Math.max(0, versions.size() - 1));
    }

    /**
     * Appends a snapshot of the declared values, but only when they differ from the most
     * recent one. Repeating an unchanged snapshot on every inspection would inflate the
     * product-change risk factor and drown the real changes.
     *
     * @return the new version when one was written, otherwise empty
     */
    @Transactional
    public Optional<ProductVersion> recordVersionIfChanged(Product product,
                                                           Map<String, String> declaredValues,
                                                           VersionSource source) {
        String mrp = declaredValues.get(com.lmguard.entity.enums.ProductField.MRP);
        String netQuantity = declaredValues.get(com.lmguard.entity.enums.ProductField.NET_QUANTITY);
        String manufacturer = declaredValues.get(com.lmguard.entity.enums.ProductField.MANUFACTURER);
        String origin = declaredValues.get(com.lmguard.entity.enums.ProductField.ORIGIN);
        String consumerCare = declaredValues.get(com.lmguard.entity.enums.ProductField.CONSUMER_CARE);

        Optional<ProductVersion> latest =
                productVersionRepository.findFirstByProductIdOrderByVersionNumberDesc(product.getId());

        if (latest.isPresent() && unchanged(latest.get(), mrp, netQuantity, manufacturer, origin, consumerCare)) {
            log.debug("Declared values unchanged for product {}; no new version written", product.getId());
            return Optional.empty();
        }

        int nextVersion = productVersionRepository.findMaxVersionNumber(product.getId()) + 1;
        ProductVersion version = productVersionRepository.save(ProductVersion.builder()
                .product(product)
                .versionNumber(nextVersion)
                .mrp(truncate(mrp, 100))
                .netQuantity(truncate(netQuantity, 100))
                .manufacturer(truncate(manufacturer, 255))
                .origin(truncate(origin, 150))
                .consumerCare(truncate(consumerCare, 500))
                .source(source)
                .build());

        log.info("Recorded product version {} for product {}", nextVersion, product.getId());
        return Optional.of(version);
    }

    /** Number of recorded changes: the first snapshot is a baseline, not a change. */
    @Transactional(readOnly = true)
    public long changeCount(UUID productId) {
        return Math.max(0, productVersionRepository.countByProductId(productId) - 1);
    }

    private boolean unchanged(ProductVersion latest, String mrp, String netQuantity,
                              String manufacturer, String origin, String consumerCare) {
        return Objects.equals(latest.getMrp(), truncate(mrp, 100))
                && Objects.equals(latest.getNetQuantity(), truncate(netQuantity, 100))
                && Objects.equals(latest.getManufacturer(), truncate(manufacturer, 255))
                && Objects.equals(latest.getOrigin(), truncate(origin, 150))
                && Objects.equals(latest.getConsumerCare(), truncate(consumerCare, 500));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normaliseCategory(String category) {
        String trimmed = trimToNull(category);
        return trimmed == null ? null : trimmed.toUpperCase(java.util.Locale.ROOT);
    }
}
