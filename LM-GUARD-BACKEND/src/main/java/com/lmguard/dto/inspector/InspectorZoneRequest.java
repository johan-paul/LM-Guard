package com.lmguard.dto.inspector;

import jakarta.validation.constraints.NotBlank;

public record InspectorZoneRequest(

        @NotBlank(message = "zone is required")
        String zone
) {
}
