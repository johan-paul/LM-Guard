package com.lmguard.service;

import com.lmguard.entity.User;
import com.lmguard.entity.enums.Role;
import com.lmguard.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The production first-admin bootstrap this fix added, now that public self-registration can no
 * longer create one. Confirms it only ever acts when explicitly configured, and refuses to run
 * at all once any administrator already exists - so leaving it enabled by accident can't be
 * used to mint a second one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Admin bootstrap seeder")
class AdminBootstrapSeederTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AdminBootstrapSeeder seeder(String email, String password, String name) {
        AdminBootstrapSeeder seeder = new AdminBootstrapSeeder(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(seeder, "bootstrapEmail", email);
        ReflectionTestUtils.setField(seeder, "bootstrapPassword", password);
        ReflectionTestUtils.setField(seeder, "bootstrapName", name);
        return seeder;
    }

    @Test
    @DisplayName("does nothing if an administrator already exists, even when fully configured")
    void skipsWhenAdminAlreadyExists() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        seeder("ops@example.test", "Str0ngPassw0rd!", "Ops Admin").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("does nothing and does not throw when email/password are not configured")
    void skipsWhenNotConfigured() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);

        seeder("", "", "Administrator").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("does nothing when the configured password is too short")
    void skipsWhenPasswordTooShort() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);

        seeder("ops@example.test", "short", "Ops Admin").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("does nothing when an account already exists for the configured email")
    void skipsWhenEmailTaken() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmailIgnoreCase("ops@example.test")).thenReturn(true);

        seeder("ops@example.test", "Str0ngPassw0rd!", "Ops Admin").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("creates exactly one ADMIN account when properly configured and none exists yet")
    void createsAdminWhenConfigured() {
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(0L);
        when(userRepository.existsByEmailIgnoreCase("ops@example.test")).thenReturn(false);
        when(passwordEncoder.encode("Str0ngPassw0rd!")).thenReturn("hashed");

        seeder("ops@example.test", "Str0ngPassw0rd!", "Ops Admin").run(null);

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().getEmail()).isEqualTo("ops@example.test");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
    }
}
