package com.lmguard.dto.inspection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Attaches a product to an inspection the inspector already has open - the product-identification
 * step of the six-step workflow. Same shape as the inline-product fields on creation. */
@Schema(name = "InspectionProductRequest", description = "Identify/attach a product to an open inspection")
public record InspectionProductRequest(

        @Schema(description = "Existing product. Omit to register one inline from the fields below.")
        UUID productId,

        @Size(max = 255, message = "productName must be at most 255 characters")
        String productName,

        @Size(max = 255, message = "brand must be at most 255 characters")
        String brand,

        @Size(max = 100, message = "category must be at most 100 characters")
        String category,

        @Size(max = 100, message = "barcode must be at most 100 characters")
        String barcode
) {
    public boolean createsProductInline() {
        return productId == null;
    }
}
