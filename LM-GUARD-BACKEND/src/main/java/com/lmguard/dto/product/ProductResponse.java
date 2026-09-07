package com.lmguard.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ProductResponse", description = "A packaged commodity")
public record ProductResponse(
        UUID id,
        String productName,
        String brand,
        String category,
        String barcode,
        Instant createdAt,
        Instant updatedAt,

        @Schema(description = "The most recently captured package photo for this product, if any")
        String imageUrl
) {
}
