package com.lmguard.service;

import com.lmguard.entity.User;
import com.lmguard.entity.enums.Role;
import com.lmguard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the very first administrator account for a real deployment, where {@link
 * DemoUserSeeder} is disabled (it seeds a fixed demo roster and is meant for development only).
 *
 * <p>Public self-registration ({@code POST /auth/register}) can never create an {@code ADMIN}
 * account - see {@link AuthService#register}. That means a fresh production database, seeded
 * from migrations alone, would have no way to create its first administrator at all. This
 * runner closes that gap without opening a public admin-creation endpoint: it is off by
 * default, reads credentials only from environment variables (never hardcoded), and - even if
 * left enabled by mistake - refuses to run once any administrator already exists, so it cannot
 * be used to mint a second one.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "lmguard.security.bootstrap-admin-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${lmguard.security.bootstrap-admin-email:}")
    private String bootstrapEmail;

    @Value("${lmguard.security.bootstrap-admin-password:}")
    private String bootstrapPassword;

    @Value("${lmguard.security.bootstrap-admin-name:Administrator}")
    private String bootstrapName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            log.debug("Admin bootstrap skipped - an administrator account already exists");
            return;
        }
        if (bootstrapEmail == null || bootstrapEmail.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.warn("lmguard.security.bootstrap-admin-enabled is true, but "
                    + "BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD are not set - no administrator "
                    + "account was created. Set both environment variables and restart.");
            return;
        }
        if (bootstrapPassword.length() < 8) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD is too short (minimum 8 characters) - "
                    + "no administrator account was created.");
            return;
        }

        String email = bootstrapEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.warn("Admin bootstrap skipped - an account already exists for {}", email);
            return;
        }

        userRepository.save(User.builder()
                .name(bootstrapName)
                .email(email)
                .passwordHash(passwordEncoder.encode(bootstrapPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        log.info("Bootstrapped the first administrator account: {}", email);
    }
}
