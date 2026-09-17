package com.lmguard.service;

import com.lmguard.dto.inspection.InspectionSummaryResponse;
import com.lmguard.dto.product.OnlineListingResponse;
import com.lmguard.dto.product.ProductCreateRequest;
import com.lmguard.dto.product.ProductHistoryResponse;
import com.lmguard.dto.product.ProductInspectionSummaryResponse;
import com.lmguard.dto.product.ProductResponse;
import com.lmguard.dto.product.ProductRiskResponse;
import com.lmguard.dto.product.ProductSummaryResponse;
import com.lmguard.dto.product.ProductVersionEntry;
import com.lmguard.dto.product.ProductVersionResponse;
import com.lmguard.dto.violation.ViolationSummaryResponse;
import com.lmguard.ai.AIAnalysisResult;
import com.lmguard.ai.AIAnalysisService;
import com.lmguard.ai.ExtractedFact;
import com.lmguard.config.properties.StorageProperties;
import com.lmguard.entity.Inspection;
import com.lmguard.entity.OnlineListing;
import com.lmguard.entity.Product;
import com.lmguard.entity.ProductVersion;
import com.lmguard.entity.RiskScore;
import com.lmguard.entity.enums.InspectionStatus;
import com.lmguard.entity.enums.ProductField;
import com.lmguard.entity.enums.RiskLevel;
import com.lmguard.entity.enums.VersionSource;
import com.lmguard.entity.enums.ViolationCaseStatus;
import com.lmguard.entity.enums.ViolationStatus;
import com.lmguard.exception.AiServiceException;
import com.lmguard.exception.BadRequestException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.InspectionMapper;
import com.lmguard.mapper.ProductMapper;
import com.lmguard.mapper.ViolationCaseMapper;
import com.lmguard.repository.InspectionRepository;
import com.lmguard.repository.OnlineListingRepository;
import com.lmguard.repository.ProductRepository;
import com.lmguard.repository.ProductVersionRepository;
import com.lmguard.repository.RiskScoreRepository;
import com.lmguard.repository.ViolationRepository;
import com.lmguard.storage.FileStorageService;
import com.lmguard.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    private static final List<ViolationCaseStatus> OPEN_CASE_STATUSES =
            List.of(ViolationCaseStatus.OPEN, ViolationCaseStatus.UNDER_REVIEW, ViolationCaseStatus.ESCALATED);

    private final ProductRepository productRepository;
    private final ProductVersionRepository productVersionRepository;
    private final InspectionRepository inspectionRepository;
    private final ViolationRepository violationRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final OnlineListingRepository onlineListingRepository;
    private final ProductMapper productMapper;
    private final InspectionMapper inspectionMapper;
    private final ViolationCaseMapper violationCaseMapper;
    private final AIAnalysisService aiAnalysisService;
    private final FileStorageService fileStorageService;

    private static final Set<String> ALLOWED_LISTING_IMAGE_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

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

    /**
     * Resolves a product from an AI/OCR reading (or an inspector's free-text entry) without
     * minting a duplicate {@link Product} row for what is really the same product read
     * slightly differently. Deliberately used only by the two paths that are *guessing* a
     * product's identity from a photo or a free-text field
     * ({@code InspectionAnalysisService#resolveProductFromAnalysis},
     * {@code InspectionService#resolveInlineProduct}) - never by {@link #create}, which is an
     * admin's explicit "register this exact product" action and must not be silently redirected
     * into reusing something else.
     *
     * <p>Matching is deliberately exact, not fuzzy: barcode first (the one identifier that
     * genuinely disambiguates), then case/edge-whitespace-insensitive name+brand. No
     * Levenshtein/similarity matching - a false merge of two different products would corrupt
     * both products' compliance history, which is worse than occasionally missing a real
     * duplicate and creating one extra row.
     *
     * @param name    required; the caller is responsible for its own "not detected" fallback
     *                (e.g. "Unidentified product") before calling this
     * @param category only used when a new product is actually created; an existing matched
     *                 product's category is never overwritten by a guess from this call
     */
    @Transactional
    public Product findOrCreate(String name, String brand, String barcode, String category) {
        String cleanBarcode = trimToNull(barcode);
        if (cleanBarcode != null) {
            Optional<Product> byBarcode = productRepository.findByBarcode(cleanBarcode);
            if (byBarcode.isPresent()) {
                log.debug("Matched existing product {} by barcode", byBarcode.get().getId());
                return byBarcode.get();
            }
        }

        String cleanName = trimToNull(name);
        String cleanBrand = trimToNull(brand);
        if (cleanName != null) {
            List<Product> byNameAndBrand = productRepository.findByNameAndBrandExact(cleanName, cleanBrand);
            if (!byNameAndBrand.isEmpty()) {
                log.debug("Matched existing product {} by name+brand", byNameAndBrand.get(0).getId());
                return byNameAndBrand.get(0);
            }
        }

        Product created = productRepository.save(Product.builder()
                .productName(cleanName == null ? "Unidentified product" : cleanName)
                .brand(cleanBrand)
                .category(normaliseCategory(category))
                .barcode(cleanBarcode)
                .build());
        log.info("Registered product {} ({}) via findOrCreate - no existing match", created.getProductName(), created.getId());
        return created;
    }

    /** Overload for callers with no category to offer (e.g. an AI reading, which never
     * produces one). */
    @Transactional
    public Product findOrCreate(String name, String brand, String barcode) {
        return findOrCreate(name, brand, barcode, null);
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String search, String category, Pageable pageable) {
        String cleanSearch = trimToNull(search);
        String cleanCategory = normaliseCategory(category);

        if (cleanSearch == null && cleanCategory == null) {
            return productRepository.findAll(pageable);
        }
        if (cleanSearch == null) {
            return productRepository.findByCategory(cleanCategory, pageable);
        }
        return productRepository.search(cleanSearch, cleanCategory, pageable);
    }

    /**
     * The registry list's compliance profile per product - latest risk score, violation counts,
     * last-inspection date. Each row costs a handful of small indexed lookups rather than one
     * join, which keeps this readable and correct at the cost of N+1 queries; acceptable at this
     * project's scale (bounded page size), consistent with the same trade-off already made in
     * {@code DashboardService#toHighRisk}.
     */
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> searchSummaries(String search, String category, Pageable pageable) {
        return search(search, category, pageable).map(this::toSummary);
    }

    private ProductSummaryResponse toSummary(Product product) {
        UUID id = product.getId();
        List<RiskScore> scores = riskScoreRepository.findByProductIdOrderByCreatedAtDesc(id);
        RiskScore latest = scores.isEmpty() ? null : scores.get(0);

        return new ProductSummaryResponse(
                id,
                product.getProductName(),
                product.getBrand(),
                product.getCategory(),
                product.getBarcode(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                latest == null ? null : latest.getTotalScore(),
                latest == null ? null : latest.getRiskLevel(),
                violationRepository.countByInspection_Product_IdAndStatus(id, ViolationStatus.NON_COMPLIANT),
                violationRepository.countByInspection_Product_IdAndCaseStatusIn(id, OPEN_CASE_STATUSES),
                inspectionRepository.findLatestCompletedAt(id),
                latestImageUrl(id));
    }

    private String latestImageUrl(UUID productId) {
        List<String> urls = inspectionRepository.findRecentImageUrls(productId, PageRequest.of(0, 1));
        return urls.isEmpty() ? null : urls.get(0);
    }

    /**
     * This product's most recent composite risk score, with the factors that actually
     * contributed - not the full itemised point breakdown ({@link RiskScore} exposes points per
     * component, which means little to an inspector without the underlying weights), but which
     * of the five named factors had any effect at all. {@code null} when the product has never
     * been scored (no inspection has run the risk engine against it yet).
     */
    @Transactional(readOnly = true)
    public ProductRiskResponse risk(UUID productId) {
        requireById(productId);
        List<RiskScore> scores = riskScoreRepository.findByProductIdOrderByCreatedAtDesc(productId);
        if (scores.isEmpty()) {
            return null;
        }
        RiskScore latest = scores.get(0);
        List<String> factors = new ArrayList<>();
        if (latest.getPreviousViolations() > 0) factors.add("Repeated declaration violations");
        if (latest.getOnlineMismatch() > 0) factors.add("Physical–digital mismatch");
        if (latest.getProductChanges() > 0) factors.add("Recent package changes");
        if (latest.getCategoryRisk() > 0) factors.add("Product category risk");
        if (latest.getRepeatIssue() > 0) factors.add("Same rule failing again");

        return new ProductRiskResponse(
                latest.getTotalScore(), latest.getRiskLevel(), factors, latest.getExplanation(), latest.getCreatedAt());
    }

    /** Every violation raised against this product, across every inspection - the Product
     * Detail screen's Violations tab. */
    @Transactional(readOnly = true)
    public List<ViolationSummaryResponse> violations(UUID productId) {
        requireById(productId);
        return violationRepository.findByInspection_Product_IdOrderByCreatedAtDesc(productId).stream()
                .map(violationCaseMapper::toSummary)
                .toList();
    }

    /**
     * The most recently captured online-marketplace listing for this product, for the
     * physical-vs-digital comparison tab. Listings are entered manually today (see
     * {@link OnlineListingRepository}) - {@code null} is the honest answer when none has been
     * captured, rather than a fabricated comparison.
     */
    @Transactional(readOnly = true)
    public OnlineListingResponse onlineListing(UUID productId) {
        requireById(productId);
        return onlineListingRepository.findFirstByProductIdOrderByCapturedAtDesc(productId)
                .map(l -> new OnlineListingResponse(
                        l.getSource(), l.getListingUrl(), l.getMrp(), l.getQuantity(), l.getManufacturer(),
                        l.getOrigin(), l.getCapturedAt()))
                .orElse(null);
    }

    /**
     * Records an online-marketplace listing by reading it off a screenshot, the same way a
     * package photo is read - OCR + the semantic (VLM) extraction step, through the exact same
     * AI service - rather than requiring someone to retype the price and quantity into a form
     * by hand. This is what actually populates {@link OnlineListingRepository}, which until now
     * had no writer at all: {@link com.lmguard.service.InspectionAnalysisService#detectOnlineMismatch}
     * already compares the most recent listing against the package's own OCR reading, but that
     * comparison could never fire because nothing ever created a listing to compare against.
     *
     * <p>The AI layer's prompt is worded for a package photograph; a listing screenshot is a
     * different kind of image (a price on a webpage, not a printed label), so extraction quality
     * here is not guaranteed to match the package pipeline's - this is a reasonable reuse of the
     * same OCR/VLM capability, not a purpose-built listing scraper.
     */
    @Transactional
    public OnlineListingResponse captureOnlineListing(UUID productId, MultipartFile file, String source, String listingUrl) {
        Product product = requireById(productId);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.IMAGE_REQUIRED, "No listing screenshot was supplied");
        }
        if (source == null || source.isBlank()) {
            throw new BadRequestException("source is required, e.g. AMAZON, FLIPKART");
        }
        String contentType = file.getContentType() == null ? null : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType == null || !ALLOWED_LISTING_IMAGE_TYPES.contains(contentType)) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE,
                    "Unsupported image type '%s'. Allowed types: %s".formatted(contentType, ALLOWED_LISTING_IMAGE_TYPES));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.INVALID_IMAGE, "Could not read the uploaded image");
        }

        StoredFile stored = fileStorageService.upload(
                StorageProperties.ONLINE_LISTINGS, bytes, file.getOriginalFilename(), contentType);

        AIAnalysisResult analysis;
        try {
            // No real Inspection backs this call - a fresh id is generated purely for the AI
            // service's own request logging/correlation, the same role inspectionId plays there.
            analysis = aiAnalysisService.analyzeImage(stored.url(), UUID.randomUUID());
        } catch (AiServiceException ex) {
            throw new BadRequestException(ErrorCode.AI_SERVICE_ERROR,
                    "Could not read the listing screenshot: " + ex.getMessage());
        }

        OnlineListing listing = onlineListingRepository.save(OnlineListing.builder()
                .product(product)
                .source(source.trim().toUpperCase(Locale.ROOT))
                .listingUrl(trimToNull(listingUrl))
                .mrp(presentValue(analysis, ProductField.MRP))
                .quantity(presentValue(analysis, ProductField.NET_QUANTITY))
                .manufacturer(presentValue(analysis, ProductField.MANUFACTURER))
                .capturedAt(Instant.now())
                .build());

        log.info("Captured online listing for product {} from {} (mrp={}, quantity={})",
                productId, listing.getSource(), listing.getMrp(), listing.getQuantity());

        return new OnlineListingResponse(listing.getSource(), listing.getListingUrl(), listing.getMrp(),
                listing.getQuantity(), listing.getManufacturer(), listing.getOrigin(), listing.getCapturedAt());
    }

    private String presentValue(AIAnalysisResult analysis, String field) {
        return analysis.fact(field).filter(ExtractedFact::isPresent).map(ExtractedFact::value).orElse(null);
    }

    /**
     * The Product History screen's cross-product declaration-change ledger, newest first. Every
     * snapshot is a real change record (a version is only ever written when a declared value
     * actually differs, per {@link #recordVersionIfChanged}) - there is no compliance-verdict
     * dimension on a snapshot itself, so every row carries the same real classification rather
     * than a fabricated one.
     */
    @Transactional(readOnly = true)
    public Page<ProductVersionEntry> historyLedger(String search, Pageable pageable) {
        String cleanSearch = trimToNull(search);
        Page<ProductVersion> versionsPage = cleanSearch == null
                ? productVersionRepository.findAllByOrderByCapturedAtDesc(pageable)
                : productVersionRepository.searchAll(cleanSearch, pageable);

        return versionsPage.map(version -> {
            Product product = version.getProduct();
            Optional<ProductVersion> previous = version.getVersionNumber() <= 1
                    ? Optional.empty()
                    : productVersionRepository.findByProductIdAndVersionNumber(product.getId(), version.getVersionNumber() - 1);

            return new ProductVersionEntry(
                    product.getId(),
                    product.getProductName(),
                    product.getBrand(),
                    product.getCategory(),
                    version.getVersionNumber(),
                    version.getMrp(),
                    version.getNetQuantity(),
                    version.getCapturedAt(),
                    previous.map(ProductVersion::getMrp).orElse(null),
                    previous.map(ProductVersion::getNetQuantity).orElse(null));
        });
    }

    @Transactional(readOnly = true)
    public Product requireById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.PRODUCT_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product product = requireById(id);
        return productMapper.toResponse(product, latestImageUrl(id));
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
     * <p>Each field is only overwritten when this run's reading is both present <em>and</em> at
     * or above {@code minConfidence} - otherwise the previous version's value for that field is
     * carried forward untouched. Without this, a single low-confidence misread (or simply a
     * field the AI didn't detect on this particular photo, e.g. from glare) would silently
     * become the product's new "current" declared value shown to every admin viewer from then
     * on, discarding a perfectly good earlier reading. {@code inspection} (nullable) records
     * which inspection produced this snapshot, so a value that does look wrong can be traced
     * back to exactly where it came from instead of just "the number is wrong, somehow."
     *
     * @return the new version when one was written, otherwise empty
     */
    @Transactional
    public Optional<ProductVersion> recordVersionIfChanged(Product product,
                                                           Inspection inspection,
                                                           Map<String, String> declaredValues,
                                                           Map<String, Double> confidences,
                                                           double minConfidence,
                                                           VersionSource source) {
        Optional<ProductVersion> latest =
                productVersionRepository.findFirstByProductIdOrderByVersionNumberDesc(product.getId());

        String mrp = resolveField(declaredValues, confidences, minConfidence, ProductField.MRP,
                latest.map(ProductVersion::getMrp).orElse(null));
        String netQuantity = resolveField(declaredValues, confidences, minConfidence, ProductField.NET_QUANTITY,
                latest.map(ProductVersion::getNetQuantity).orElse(null));
        String manufacturer = resolveField(declaredValues, confidences, minConfidence, ProductField.MANUFACTURER,
                latest.map(ProductVersion::getManufacturer).orElse(null));
        String origin = resolveField(declaredValues, confidences, minConfidence, ProductField.ORIGIN,
                latest.map(ProductVersion::getOrigin).orElse(null));
        String consumerCare = resolveField(declaredValues, confidences, minConfidence, ProductField.CONSUMER_CARE,
                latest.map(ProductVersion::getConsumerCare).orElse(null));

        if (latest.isPresent() && unchanged(latest.get(), mrp, netQuantity, manufacturer, origin, consumerCare)) {
            log.debug("Declared values unchanged for product {}; no new version written", product.getId());
            return Optional.empty();
        }

        int nextVersion = productVersionRepository.findMaxVersionNumber(product.getId()) + 1;
        ProductVersion version = productVersionRepository.save(ProductVersion.builder()
                .product(product)
                .inspection(inspection)
                .versionNumber(nextVersion)
                .mrp(truncate(mrp, 100))
                .netQuantity(truncate(netQuantity, 100))
                .manufacturer(truncate(manufacturer, 255))
                .origin(truncate(origin, 150))
                .consumerCare(truncate(consumerCare, 500))
                .source(source)
                .build());

        log.info("Recorded product version {} for product {} (inspection {})",
                nextVersion, product.getId(), inspection == null ? "none" : inspection.getId());
        return Optional.of(version);
    }

    /** This run's reading for {@code field} wins only if it's present and confident enough;
     * otherwise the product keeps whatever it last reliably had for that field. */
    private String resolveField(Map<String, String> declaredValues, Map<String, Double> confidences,
                                double minConfidence, String field, String previousValue) {
        String newValue = declaredValues.get(field);
        Double confidence = confidences.get(field);
        boolean acceptNew = newValue != null && confidence != null && confidence >= minConfidence;
        return acceptNew ? newValue : previousValue;
    }

    /** Number of recorded changes: the first snapshot is a baseline, not a change. */
    @Transactional(readOnly = true)
    public long changeCount(UUID productId) {
        return Math.max(0, productVersionRepository.countByProductId(productId) - 1);
    }

    /**
     * Aggregated inspection/violation history for a product - what the six-step workflow's
     * Product History step shows an inspector before they record their own findings. A repeat
     * violation is a rule code breached across more than one separate inspection, not merely
     * counted more than once within the same inspection.
     */
    @Transactional(readOnly = true)
    public ProductInspectionSummaryResponse inspectionSummary(UUID productId) {
        requireById(productId);

        List<Inspection> completed = inspectionRepository.findCompletedForProduct(productId);
        int compliant = (int) completed.stream().filter(i -> i.getStatus() == InspectionStatus.COMPLIANT).count();
        int nonCompliant = (int) completed.stream().filter(i -> i.getStatus() == InspectionStatus.NON_COMPLIANT).count();
        int inconclusive = (int) completed.stream().filter(i -> i.getStatus() == InspectionStatus.INCONCLUSIVE).count();

        long previousViolations = violationRepository.countByInspection_Product_IdAndStatus(
                productId, com.lmguard.entity.enums.ViolationStatus.NON_COMPLIANT);

        long repeatViolations = violationRepository.countInspectionsPerRuleCodeForProduct(productId).stream()
                .filter(row -> ((Number) row[1]).longValue() > 1)
                .count();

        RiskLevel latestRiskLevel = completed.stream()
                .map(Inspection::getRiskLevel)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        List<InspectionSummaryResponse> recent = completed.stream().map(inspectionMapper::toSummary).toList();

        return new ProductInspectionSummaryResponse(
                completed.size(), compliant, nonCompliant, inconclusive,
                (int) previousViolations, (int) repeatViolations, latestRiskLevel, recent);
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
        if (trimmed == null || "ALL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed.toUpperCase(java.util.Locale.ROOT);
    }
}
