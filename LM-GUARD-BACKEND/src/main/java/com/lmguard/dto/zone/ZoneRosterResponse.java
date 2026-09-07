package com.lmguard.dto.zone;

import com.lmguard.dto.inspector.InspectorResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "ZoneRosterResponse", description = "A zone with the officers posted to it")
public record ZoneRosterResponse(
        UUID id,
        String name,
        String code,
        String office,
        String district,
        List<InspectorResponse> inspectors,
        int total,
        int active,
        int inactive,
        long workload
) {
}
