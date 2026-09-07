package com.lmguard.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RepeatOffender", description = "A manufacturer with a sustained substantiated-violation history")
public record RepeatOffenderResponse(

        @Schema(description = "Manufacturer / brand name, as declared on the product")
        String manufacturer,

        @Schema(description = "Substantiated violations across every product carrying this brand", example = "7")
        long totalViolations,

        @Schema(description = "Distinct products carrying this brand that have a substantiated violation", example = "3")
        long trackedProducts,

        @Schema(description = "The zone where most of this manufacturer's violations were recorded - "
                + "a manufacturer is not itself zone-scoped, only the inspections that cite it are")
        String zone
) {
}
