package com.lmguard.dto.auth;

import com.lmguard.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "Create a new inspector or administrator account")
public record RegisterRequest(

        @Schema(description = "Full name", example = "A. Ramesh")
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Schema(description = "Login email; must be unique", example = "inspector@lmguard.gov.in")
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Schema(description = "Password, minimum 8 characters", example = "Str0ngPassw0rd!")
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
        String password,

        @Schema(description = "Role to assign. Defaults to INSPECTOR when omitted.", example = "INSPECTOR")
        Role role
) {
}
