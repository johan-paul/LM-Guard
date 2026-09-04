package com.lmguard.dto.inspection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Starts an inspection.
 *
 * <p>Either reference an existing product with {@code productId}, or supply
 * {@code productName} to have one created on the spot - a field inspector scanning an
 * unknown package should not have to register it first.
 */
@Schema(name = "InspectionCreateRequest", description = "Open a new inspection")
public record InspectionCreateRequest(

        @Schema(description = "Existing product to inspect. Omit to create one from the fields below.")
        UUID productId,

        @Schema(description = "Name for a product created inline. Required when productId is absent.",
                example = "Classic Salted Chips")
        @Size(max = 255, message = "productName must be at most 255 characters")
        String productName,

        @Schema(description = "Brand for a product created inline", example = "ABC Foods")
        @Size(max = 255, message = "brand must be at most 255 characters")
        String brand,

        @Schema(description = "Category for a product created inline", example = "PACKAGED_FOOD")
        @Size(max = 100, message = "category must be at most 100 characters")
        String category,

        @Schema(description = "Barcode for a product created inline", example = "8901234567890")
        @Size(max = 100, message = "barcode must be at most 100 characters")
        String barcode,

        @Schema(description = "Free-text inspector notes")
        String notes
) {

    public boolean createsProductInline() {
        return productId == null;
    }
}
