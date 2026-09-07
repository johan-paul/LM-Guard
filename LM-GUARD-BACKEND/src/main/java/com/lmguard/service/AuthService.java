package com.lmguard.service;

import com.lmguard.dto.auth.AuthResponse;
import com.lmguard.dto.auth.LoginRequest;
import com.lmguard.dto.auth.RegisterRequest;
import com.lmguard.dto.auth.UserResponse;
import com.lmguard.entity.User;
import com.lmguard.entity.enums.Role;
import com.lmguard.exception.ApiException;
import com.lmguard.exception.ConflictException;
import com.lmguard.exception.ErrorCode;
import com.lmguard.exception.ResourceNotFoundException;
import com.lmguard.mapper.UserMapper;
import com.lmguard.repository.UserRepository;
import com.lmguard.security.JwtService;
import com.lmguard.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Registration and login.
 *
 * <p>Passwords are BCrypt-hashed on the way in and never read back out. Authentication goes
 * through Spring Security's {@code AuthenticationManager} rather than a hand-rolled password
 * comparison, so account state and timing behaviour stay consistent with the rest of the
 * security stack.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final InspectorService inspectorService;

    /**
     * Public self-registration. This is deliberately the only account-creation path with no
     * authentication requirement, which is exactly why it can never be trusted to create an
     * {@code ADMIN} account on the caller's say-so - {@code role} is accepted for backward
     * compatibility with existing clients that still send {@code INSPECTOR} explicitly, but the
     * only value this method will ever act on is {@code INSPECTOR}; anything else is refused
     * outright rather than silently downgraded, so a caller can't mistake a rejected request for
     * a successful one. An ADMIN account can only be created by seeding (see
     * {@code DemoUserSeeder} / {@code AdminBootstrapSeeder}) or by an existing administrator
     * through the admin-management surface - never through this endpoint.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Public registration cannot create administrator accounts");
        }

        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.INSPECTOR)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Registered {} account {}", saved.getRole(), saved.getEmail());

        UserPrincipal principal = UserPrincipal.from(saved);
        return AuthResponse.of(jwtService.generateToken(principal), jwtService.expiryFromNow(),
                userMapper.toResponse(saved));
    }

    // Writes InspectorProfile.lastActiveAt on a successful inspector login - not read-only.
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normaliseEmail(request.email());

        // Throws BadCredentialsException / DisabledException, which the global handler maps.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() == Role.INSPECTOR) {
            inspectorService.recordLogin(user.getId());
        }

        log.info("Login succeeded for {}", user.getEmail());
        return AuthResponse.of(jwtService.generateToken(principal), jwtService.expiryFromNow(),
                userMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(java.util.UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
