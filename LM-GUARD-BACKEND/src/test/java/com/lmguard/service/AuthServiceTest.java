package com.lmguard.service;

import com.lmguard.config.properties.JwtProperties;
import com.lmguard.dto.auth.AuthResponse;
import com.lmguard.dto.auth.LoginRequest;
import com.lmguard.dto.auth.RegisterRequest;
import com.lmguard.entity.User;
import com.lmguard.entity.enums.Role;
import com.lmguard.exception.ApiException;
import com.lmguard.exception.ConflictException;
import com.lmguard.mapper.UserMapper;
import com.lmguard.repository.UserRepository;
import com.lmguard.security.JwtService;
import com.lmguard.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Public account creation and login.
 *
 * <p>The one property this class exists to pin down: {@code POST /auth/register} can never be
 * used to create an {@code ADMIN} account, under any spelling of the role a caller sends -
 * because the service ignores the field entirely for the value it actually stores, rather than
 * trying to validate against a blocklist. Login (for both roles) and JWT issuance are otherwise
 * untouched by that fix, which these tests also pin down so a future change can't quietly widen
 * the hole back open.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Auth service")
class AuthServiceTest {

    private static final String JWT_SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWxtZ3VhcmQtdW5pdC10ZXN0cy0zMmJ5dGVzKw==";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private InspectorService inspectorService;

    private final JwtService jwtService = new JwtService(new JwtProperties(JWT_SECRET, 3_600_000L, "lm-guard-test"));
    private final UserMapper userMapper = new UserMapper();

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder, authenticationManager, jwtService, userMapper,
                inspectorService);
    }

    private User savedUserWithRole(Role role) {
        User user = User.builder().name("Test User").email("test@example.test")
                .passwordHash("hashed").role(role).enabled(true).build();
        user.setId(UUID.randomUUID());
        return user;
    }

    // ---- TEST 1: INSPECTOR registration succeeds ----

    @Test
    @DisplayName("public registration with role=INSPECTOR succeeds and creates an INSPECTOR account")
    void registerInspectorSucceeds() {
        when(userRepository.existsByEmailIgnoreCase("new.inspector@example.test")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthResponse response = service().register(
                new RegisterRequest("New Inspector", "new.inspector@example.test", "Str0ngPassw0rd!", Role.INSPECTOR));

        assertThat(response.user().role()).isEqualTo(Role.INSPECTOR);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.INSPECTOR);
    }

    @Test
    @DisplayName("public registration with role omitted (null) defaults to INSPECTOR, same as before")
    void registerNullRoleDefaultsToInspector() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthResponse response = service().register(
                new RegisterRequest("New Inspector", "another@example.test", "Str0ngPassw0rd!", null));

        assertThat(response.user().role()).isEqualTo(Role.INSPECTOR);
    }

    // ---- TEST 2: ADMIN registration is rejected ----

    @Test
    @DisplayName("public registration with role=ADMIN is rejected and no account is created")
    void registerAdminIsRejected() {
        assertThatThrownBy(() -> service().register(
                new RegisterRequest("Attacker", "attacker@example.test", "Str0ngPassw0rd!", Role.ADMIN)))
                .isInstanceOf(ApiException.class);

        verify(userRepository, never()).save(any());
    }

    // ---- TEST 3: cannot bypass via case variation / invalid / manipulated values ----
    // Jackson's default enum binding is case-sensitive and rejects unknown values outright
    // (before this method is even called) - the only values that ever reach the service as
    // `Role.role()` are INSPECTOR, ADMIN or null, so those three ARE the exhaustive bypass
    // surface. All three are covered above and here.

    @Test
    @DisplayName("Role.valueOf rejects a lowercase or otherwise misspelled role before it reaches the service")
    void roleEnumRejectsCaseVariantsAtDeserialisation() {
        assertThatThrownBy(() -> Role.valueOf("admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Role.valueOf("Admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Role.valueOf("SUPERADMIN")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("registering twice with the same email is rejected on the second attempt regardless of role requested")
    void duplicateEmailRejectedRegardlessOfRole() {
        when(userRepository.existsByEmailIgnoreCase("dup@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service().register(
                new RegisterRequest("Someone", "dup@example.test", "Str0ngPassw0rd!", Role.INSPECTOR)))
                .isInstanceOf(ConflictException.class);
    }

    // ---- TEST 4 / 5: existing ADMIN and INSPECTOR login still work ----

    private void stubAuthenticatedAs(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("an existing ADMIN account can still log in and receives a valid token")
    void adminLoginStillWorks() {
        User admin = savedUserWithRole(Role.ADMIN);
        stubAuthenticatedAs(admin);

        AuthResponse response = service().login(new LoginRequest(admin.getEmail(), "admin123"));

        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
        assertThat(jwtService.extractUserId(jwtService.parseClaims(response.token()))).isEqualTo(admin.getId());
        verify(inspectorService, never()).recordLogin(any());
    }

    @Test
    @DisplayName("an existing INSPECTOR account can still log in and receives a valid token")
    void inspectorLoginStillWorks() {
        User inspector = savedUserWithRole(Role.INSPECTOR);
        stubAuthenticatedAs(inspector);

        AuthResponse response = service().login(new LoginRequest(inspector.getEmail(), "inspect123"));

        assertThat(response.user().role()).isEqualTo(Role.INSPECTOR);
        assertThat(jwtService.extractUserId(jwtService.parseClaims(response.token()))).isEqualTo(inspector.getId());
        verify(inspectorService).recordLogin(inspector.getId());
    }

    // ---- TEST 8: JWT behaviour unchanged (issued token carries the real role/id) ----

    @Test
    @DisplayName("the issued token's role claim matches the account's real role")
    void tokenCarriesRealRole() {
        User admin = savedUserWithRole(Role.ADMIN);
        stubAuthenticatedAs(admin);

        AuthResponse response = service().login(new LoginRequest(admin.getEmail(), "admin123"));

        String roleClaim = jwtService.parseClaims(response.token()).get(JwtService.CLAIM_ROLE, String.class);
        assertThat(roleClaim).isEqualTo("ADMIN");
    }
}
