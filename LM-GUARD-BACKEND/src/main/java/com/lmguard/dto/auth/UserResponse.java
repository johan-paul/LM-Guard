package com.lmguard.dto.auth;

import com.lmguard.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** A user as exposed over the API. The password hash is never part of this shape. */
@Schema(name = "UserResponse", description = "A platform user")
public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt
) {
}
