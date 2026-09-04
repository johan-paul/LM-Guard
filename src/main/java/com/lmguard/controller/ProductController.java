package com.lmguard.controller;

import com.lmguard.common.ApiResponse;
import com.lmguard.common.PageResponse;
import com.lmguard.dto.product.ProductCreateRequest;
import com.lmguard.dto.product.ProductHistoryResponse;
import com.lmguard.dto.product.ProductResponse;
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
            description = "Free-text `search` matches product name, brand or barcode.")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> list(
            @Parameter(description = "Free-text match on name, brand or barcode")
            @RequestParam(required = false) String search,
            @Parameter(description = "Exact category filter, e.g. PACKAGED_FOOD")
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var results = productService.search(search, category,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(results, productMapper::toResponse)));
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
}
