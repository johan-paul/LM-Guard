package com.lmguard.dto.product;

import com.lmguard.entity.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "ProductRisk", description = "This product's most recent composite risk score, with the factors that actually contributed")
public record ProductRiskResponse(

        int riskScore,
        RiskLevel riskLevel,

        @Schema(description = "Which of the five weighted factors contributed a non-zero number of points to this score")
        List<String> factors,

        String explanation,
        Instant assessedAt
) {
}
