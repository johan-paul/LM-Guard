package com.lmguard.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ProductVersionEntry", description = "One declaration-change ledger row, across every product")
public record ProductVersionEntry(

        UUID productId,
        String productName,
        String brand,
        String category,
        int versionNumber,
        String mrp,
        String netQuantity,
        Instant capturedAt,

        @Schema(description = "This product's immediately preceding snapshot, if this isn't its first")
        String previousMrp,
        String previousNetQuantity
) {
}
