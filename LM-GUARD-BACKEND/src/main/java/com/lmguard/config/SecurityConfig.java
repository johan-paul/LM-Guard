package com.lmguard.config;

import com.lmguard.security.JwtAuthenticationEntryPoint;
import com.lmguard.security.JwtAuthenticationFilter;
import com.lmguard.security.RestAccessDeniedHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT security.
 *
 * <p>Authorisation model:
 * <ul>
 *   <li>{@code /api/auth/**} - open, so accounts can be created and tokens obtained.</li>
 *   <li>Swagger and actuator health - open in dev; Swagger is switched off entirely in prod.</li>
 *   <li>Everything else under {@code /api/**} - authenticated.</li>
 *   <li>Rule management and the global dashboard - ADMIN only.</li>
 * </ul>
 *
 * <p>CSRF is disabled because there is no cookie-based session to forge: the token travels in
 * an {@code Authorization} header that a cross-site form cannot set.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final UserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // --- public ---
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/actuator/health", "/actuator/info",
                                "/error").permitAll()
                        // Locally stored images, served only when storage.provider=local.
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()

                        // --- ADMIN only ---
                        // Reading the rule catalog (GET) is open to any authenticated user - the
                        // six-step workflow's Findings step cites rules; only managing them is
                        // ADMIN-only (also enforced by @PreAuthorize on those two methods).
                        .requestMatchers(HttpMethod.POST, "/api/rules/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/rules/**").hasRole("ADMIN")
                        .requestMatchers("/api/dashboard/global").hasRole("ADMIN")
                        .requestMatchers("/api/inspectors/**").hasRole("ADMIN")

                        // --- everything else ---
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // Still runs the hashing routine for unknown emails, so response timing does not
        // reveal whether an account exists.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
