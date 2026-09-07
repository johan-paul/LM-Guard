package com.lmguard.security;

import com.lmguard.config.properties.JwtProperties;
import com.lmguard.entity.enums.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWT service")
class JwtServiceTest {

    private static final String SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWxtZ3VhcmQtdW5pdC10ZXN0cy0zMmJ5dGVzKw==";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, 3_600_000L, "lm-guard-test"));

    private UserPrincipal principal() {
        return new UserPrincipal(UUID.randomUUID(), "Test Inspector", "inspector@example.test",
                "hash", Role.INSPECTOR, true);
    }

    @Test
    @DisplayName("round-trips a token with its claims")
    void roundTrip() {
        UserPrincipal principal = principal();

        Claims claims = jwtService.parseClaims(jwtService.generateToken(principal));

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(principal.getEmail());
        assertThat(claims.getIssuer()).isEqualTo("lm-guard-test");
        assertThat(jwtService.extractUserId(claims)).isEqualTo(principal.getId());
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("INSPECTOR");
    }

    @Test
    @DisplayName("rejects a tampered, malformed or missing token without throwing")
    void rejectsBadTokens() {
        String valid = jwtService.generateToken(principal());

        // Flipping the last character (rather than appending one) reliably corrupts the
        // decoded signature bytes regardless of base64url group alignment: appending a
        // character can, depending on the signature's encoded length, complete a new base64
        // group that a lenient decoder still parses "successfully" into a different byte
        // count, which does not exercise the tamper-rejection path this test means to check.
        char lastChar = valid.charAt(valid.length() - 1);
        char replacement = lastChar == 'A' ? 'B' : 'A';
        String tampered = valid.substring(0, valid.length() - 1) + replacement;

        assertThat(jwtService.parseClaims(tampered)).isNull();
        assertThat(jwtService.parseClaims("not-a-jwt")).isNull();
        assertThat(jwtService.parseClaims("")).isNull();
        assertThat(jwtService.parseClaims(null)).isNull();
    }

    @Test
    @DisplayName("rejects a token signed with a different key")
    void rejectsForeignSignature() {
        JwtService other = new JwtService(new JwtProperties(
                "YW5vdGhlci1zZWNyZXQta2V5LXRoYXQtaXMtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZyE=",
                3_600_000L, "lm-guard-test"));

        assertThat(jwtService.parseClaims(other.generateToken(principal()))).isNull();
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpired() throws InterruptedException {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, 1L, "lm-guard-test"));
        String token = shortLived.generateToken(principal());

        Thread.sleep(50);

        assertThat(shortLived.parseClaims(token)).isNull();
    }

    @Test
    @DisplayName("refuses to start with a missing or too-short signing key")
    void refusesWeakKeys() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties(null, 3_600_000L, "lm-guard")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", 3_600_000L, "lm-guard")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
