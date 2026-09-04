package com.lmguard.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ProductCreateRequest", description = "Register a packaged commodity for inspection")
public record ProductCreateRequest(

        @Schema(description = "Product name as printed on the package", example = "Classic Salted Chips")
        @NotBlank(message = "productName is required")
        @Size(max = 255, message = "productName must be at most 255 characters")
        String productName,

        @Schema(description = "Brand name", example = "ABC Foods")
        @Size(max = 255, message = "brand must be at most 255 characters")
        String brand,

        @Schema(description = "Category. Drives the category component of the risk score.", example = "PACKAGED_FOOD")
        @Size(max = 100, message = "category must be at most 100 characters")
        String category,

        @Schema(description = "Barcode / EAN, if printed", example = "8901234567890")
        @Size(max = 100, message = "barcode must be at most 100 characters")
        String barcode
) {
}
