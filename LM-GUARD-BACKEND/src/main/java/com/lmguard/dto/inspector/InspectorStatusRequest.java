package com.lmguard.dto.inspector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InspectorStatusRequest(

        @NotBlank(message = "status is required")
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "status must be ACTIVE or INACTIVE")
        String status
) {
}
