package com.lmguard.dto.zone;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "ZoneResponse", description = "A Legal Metrology zone")
public record ZoneResponse(
        UUID id,
        String name,
        String code,
        String office,
        String district
) {
}
