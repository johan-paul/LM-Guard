package com.lmguard.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Exchange credentials for a JWT")
public record LoginRequest(

        @Schema(description = "Registered email", example = "inspector@lmguard.gov.in")
        @NotBlank(message = "email is required")
        String email,

        @Schema(description = "Account password", example = "Str0ngPassw0rd!")
        @NotBlank(message = "password is required")
        String password
) {
}
