package com.lmguard.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "OnlineListing", description = "Declared values captured from an online marketplace listing, for physical-vs-digital comparison")
public record OnlineListingResponse(

        @Schema(example = "AMAZON") String source,
        String listingUrl,
        String mrp,
        String quantity,
        String manufacturer,
        String origin,
        Instant capturedAt
) {
}
