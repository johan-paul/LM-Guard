package com.lmguard.security;

import com.lmguard.entity.enums.Role;
import com.lmguard.exception.ApiException;
import com.lmguard.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/** Reads the authenticated principal out of the security context. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** The authenticated user, or a 401 if there is none. */
    public static UserPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    public static UUID currentUserId() {
        return requirePrincipal().getId();
    }

    public static boolean isAdmin() {
        return currentPrincipal().map(principal -> principal.getRole() == Role.ADMIN).orElse(false);
    }
}
