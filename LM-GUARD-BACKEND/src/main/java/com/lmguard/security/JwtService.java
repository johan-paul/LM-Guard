package com.lmguard.security;

import com.lmguard.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and verifies the HS256 tokens used for API authentication.
 *
 * <p>The signing key comes from {@code JWT_SECRET} and is validated at startup: an absent or
 * too-short key fails the application immediately rather than silently producing tokens that
 * are trivial to forge.
 */
@Service
@Slf4j
public class JwtService {

    private static final int MINIMUM_KEY_BYTES = 32; // 256 bits, the floor for HS256.

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NAME = "name";

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = buildKey(properties.secret());
    }

    /**
     * Accepts a Base64 secret (the documented form) and falls back to raw UTF-8 bytes so a
     * developer who pasted a plain passphrase gets a working key rather than a confusing
     * decoding error - as long as it is long enough to be safe.
     */
    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Generate one with: openssl rand -base64 48");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            byte[] rawBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (rawBytes.length >= MINIMUM_KEY_BYTES) {
                keyBytes = rawBytes;
            } else {
                throw new IllegalStateException(
                        "JWT_SECRET is too short: HS256 needs at least 256 bits (32 bytes). "
                                + "Generate one with: openssl rand -base64 48");
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.expirationMs());

        return Jwts.builder()
                .subject(principal.getEmail())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(
                        CLAIM_USER_ID, principal.getId().toString(),
                        CLAIM_ROLE, principal.getRole().name(),
                        CLAIM_NAME, principal.getName()))
                .signWith(signingKey)
                .compact();
    }

    public Instant expiryFromNow() {
        return Instant.now().plusMillis(properties.expirationMs());
    }

    /**
     * Verifies the signature, issuer and expiry.
     *
     * @return the claims, or {@code null} when the token is missing, malformed, expired or
     *         signed with the wrong key. Callers treat null as "not authenticated"; nothing
     *         about why is revealed to the client.
     */
    public Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.debug("Rejected expired JWT for subject {}", ex.getClaims().getSubject());
            return null;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected invalid JWT: {}", ex.getMessage());
            return null;
        }
    }

    public UUID extractUserId(Claims claims) {
        String raw = claims.get(CLAIM_USER_ID, String.class);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
