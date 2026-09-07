package com.lmguard.dto.inspector;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "InspectorCreateRequest", description = "Add an inspecting officer to the directory")
public record InspectorCreateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size(max = 255, message = "fullName must be at most 255 characters")
        String fullName,

        @Size(max = 50, message = "rank must be at most 50 characters")
        String rank,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "phone is required")
        @Size(max = 30, message = "phone must be at most 30 characters")
        String phone,

        @Schema(description = "Zone name, e.g. \"Coimbatore North\". Must already exist.")
        String zone,

        @Schema(description = "ACTIVE or INACTIVE. Defaults to ACTIVE.")
        String status
) {
}
