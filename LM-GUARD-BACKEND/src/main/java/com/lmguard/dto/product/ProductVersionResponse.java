package com.lmguard.dto.product;

import com.lmguard.entity.enums.VersionSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ProductVersionResponse",
        description = "A point-in-time snapshot of a product's declared facts")
public record ProductVersionResponse(
        UUID id,
        int versionNumber,
        String mrp,
        String netQuantity,
        String manufacturer,
        String origin,
        String consumerCare,
        Instant capturedAt,
        VersionSource source
) {
}
