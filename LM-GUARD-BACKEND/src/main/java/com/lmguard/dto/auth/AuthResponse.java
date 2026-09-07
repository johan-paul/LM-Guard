package com.lmguard.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "AuthResponse", description = "Issued access token and the authenticated user")
public record AuthResponse(

        @Schema(description = "JWT bearer token. Send as: Authorization: Bearer <token>")
        String token,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Absolute expiry time of the token (UTC)")
        Instant expiresAt,

        @Schema(description = "The authenticated user")
        UserResponse user
) {

    public static AuthResponse of(String token, Instant expiresAt, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresAt, user);
    }
}
