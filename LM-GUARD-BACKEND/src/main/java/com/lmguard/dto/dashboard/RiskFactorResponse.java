package com.lmguard.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RiskFactor", description = "One weighted contributor to the composite risk score, as actually configured")
public record RiskFactorResponse(

        String factor,

        @Schema(description = "Points this factor contributes at full strength, as a share of the maximum score", example = "30")
        int weight,

        @Schema(description = "Risk scores where this factor contributed a non-zero number of points", example = "46")
        long occurrences
) {
}
