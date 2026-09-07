package com.lmguard.dto.inspector;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InspectorUpdateRequest(

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

        String zone,

        String status
) {
}
