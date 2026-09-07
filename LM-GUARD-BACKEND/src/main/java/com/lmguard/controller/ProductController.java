package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.common.PageResponse;
import com.lmguard.dto.product.OnlineListingResponse;
import com.lmguard.dto.product.ProductCreateRequest;
import com.lmguard.dto.product.ProductHistoryResponse;
import com.lmguard.dto.product.ProductInspectionSummaryResponse;
import com.lmguard.dto.product.ProductResponse;
import com.lmguard.dto.product.ProductRiskResponse;
import com.lmguard.dto.product.ProductSummaryResponse;
import com.lmguard.dto.product.ProductVersionEntry;
import com.lmguard.dto.violation.ViolationSummaryResponse;
import com.lmguard.mapper.ProductMapper;
import com.lmguard.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "2. Products", description = "Registry of packaged commodities and their declaration history")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    @PostMapping
    @Operation(summary = "Register a product",
            description = """
                    Registers a packaged commodity. Declared values (MRP, net quantity, ...) are
                    **not** set here - they are captured per inspection as versioned snapshots,
                    which is what makes label changes visible over time.

                    `category` feeds the category component of the risk score; it is upper-cased
                    on save so it matches the configured high-risk list.
                    """)
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product registered", productService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List and search products",
            description = "Free-text `search` matches product name, brand or barcode. Each row carries "
                    + "its current compliance profile - latest risk score, violation counts, last "
                    + "inspection date - for the registry list.")
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> list(
            @Parameter(description = "Free-text match on name, brand or barcode")
            @RequestParam(required = false) String search,
            @Parameter(description = "Exact category filter, e.g. PACKAGED_FOOD")
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var results = productService.searchSummaries(search, category,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(results)));
    }

    @GetMapping("/history")
    @Operation(summary = "Cross-product declaration-change ledger",
            description = "Every recorded declaration snapshot across every product, newest first - "
                    + "the Product History screen. A row is only ever written when a declared value "
                    + "actually changed.")
    public ResponseEntity<ApiResponse<PageResponse<ProductVersionEntry>>> historyLedger(
            @Parameter(description = "Free-text match on product name or brand")
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var results = productService.historyLedger(search, PageRequest.of(page, Math.min(size, 200)));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(results)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one product")
    public ResponseEntity<ApiResponse<ProductResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(id)));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Declaration history for a product",
            description = """
                    Every recorded snapshot of the product's declared values, newest first.
                    A snapshot is only appended when a value actually changed, so `changeCount`
                    is a count of real label changes rather than of inspections.
                    """)
    public ResponseEntity<ApiResponse<ProductHistoryResponse>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.history(id)));
    }

    @GetMapping("/{id}/inspection-summary")
    @Operation(summary = "Inspection/violation history for a product",
            description = """
                    What the six-step workflow's Product History step shows an inspector before
                    they record their own findings: previous inspections, their verdicts, total
                    violations, and which rule codes have been breached more than once.
                    """)
    public ResponseEntity<ApiResponse<ProductInspectionSummaryResponse>> inspectionSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.inspectionSummary(id)));
    }

    @GetMapping("/{id}/risk")
    @Operation(summary = "This product's current composite risk score",
            description = "The most recent score, with the factors that actually contributed - "
                    + "`null` if this product has never been through the risk engine.")
    public ResponseEntity<ApiResponse<ProductRiskResponse>> risk(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.risk(id)));
    }

    @GetMapping("/{id}/violations")
    @Operation(summary = "Every violation raised against this product", description = "Across every inspection, newest first.")
    public ResponseEntity<ApiResponse<List<ViolationSummaryResponse>>> violations(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.violations(id)));
    }

    @GetMapping("/{id}/online-listing")
    @Operation(summary = "Most recent online-marketplace listing for this product",
            description = "For the physical-vs-digital comparison tab. Listings are captured manually today; "
                    + "`null` when none has been recorded for this product.")
    public ResponseEntity<ApiResponse<OnlineListingResponse>> onlineListing(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.onlineListing(id)));
    }
}
