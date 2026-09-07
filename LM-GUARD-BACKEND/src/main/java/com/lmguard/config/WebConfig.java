package com.lmguard.config;

import com.lmguard.config.properties.CorsProperties;
import com.lmguard.config.properties.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * CORS for the React frontend, plus static serving of locally stored images.
 *
 * <p>Allowed origins are an explicit, configurable list. A wildcard origin under the
 * {@code prod} profile fails startup: shipping {@code Access-Control-Allow-Origin: *}
 * alongside credentials is a mistake that is much cheaper to catch here than in production.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;
    private final StorageProperties storageProperties;
    private final Environment environment;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && corsProperties.hasWildcardOrigin()) {
            throw new IllegalStateException(
                    "Refusing to start: lmguard.cors.allowed-origins is '*' under the prod profile. "
                            + "Set FRONTEND_URL to your actual frontend origin.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.originList());
        configuration.setAllowedMethods(corsProperties.methodList());
        configuration.setAllowedHeaders(corsProperties.headerList());
        configuration.setAllowCredentials(corsProperties.allowCredentials());
        configuration.setMaxAge(corsProperties.maxAge());
        configuration.setExposedHeaders(java.util.List.of("Authorization", "Content-Disposition"));

        log.info("CORS allowed origins: {}", corsProperties.originList());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/files/**", configuration);
        return source;
    }

    /**
     * Serves files written by the local storage provider so package images render in the
     * browser during development without a Supabase bucket. Not registered for Supabase
     * storage, where images are served by Supabase itself.
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        if (storageProperties.isSupabase()) {
            return;
        }
        Path root = Paths.get(storageProperties.localDirectory()).toAbsolutePath().normalize();
        // A resource location must end in "/" to be treated as a directory. Path.toUri() only
        // appends one when the directory already exists, which it may not on a first run.
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        log.info("Serving local storage from {} at /files/**", root);
        registry.addResourceHandler("/files/**").addResourceLocations(location);
    }
}
