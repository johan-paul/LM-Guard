package com.lmguard.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret       Base64-encoded signing key, at least 256 bits. Supplied via JWT_SECRET.
 * @param expirationMs Token lifetime in milliseconds.
 * @param issuer       Value placed in the {@code iss} claim.
 */
@ConfigurationProperties(prefix = "lmguard.jwt")
public record JwtProperties(
        String secret,
        long expirationMs,
        String issuer
) {

    public JwtProperties {
        if (expirationMs <= 0) {
            expirationMs = 86_400_000L;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "lm-guard";
        }
    }
}
